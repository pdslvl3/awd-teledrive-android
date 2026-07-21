package com.awd.teledrive.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.awd.teledrive.core.MimeTypeHelper
import com.awd.teledrive.data.local.DriveDao
import com.awd.teledrive.data.local.DriveItemEntity
import com.awd.teledrive.data.remote.TelegramClient
import com.awd.teledrive.data.service.TransferService
import com.awd.teledrive.domain.model.DriveItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

data class UploadProgressItem(
    val fileName: String,
    val status: String, 
    val progress: Float = 0f,
    val downloadedSize: Long = 0,
    val totalSize: Long = 0,
    val uniqueId: String = "",
    val isDownload: Boolean = false
)

@Singleton
class DriveRepository @Inject constructor(
    private val telegramClient: TelegramClient,
    private val transferRepository: TransferRepository,
    private val driveDao: DriveDao,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var savedMessagesChatId: Long = 0
    private val _savedMessagesChatIdFlow = MutableStateFlow(0L)
    fun getSavedMessagesChatIdFlow(): Flow<Long> = _savedMessagesChatIdFlow.asStateFlow()

    private val exportOnComplete = mutableMapOf<String, String>()
    private var fetchJob: Job? = null

    private val _currentUploads = MutableStateFlow<List<UploadProgressItem>>(emptyList())
    val currentUploads: StateFlow<List<UploadProgressItem>> = _currentUploads.asStateFlow()

    // Gunakan Map agar unik berdasarkan ID File Telegram, bukan nama
    private val activeTasks = java.util.concurrent.ConcurrentHashMap<String, Long>() // Key: fileName, Value: fileId
    private val uploadQueue = java.util.concurrent.ConcurrentLinkedQueue<UploadTask>()
    private var activeUploads = 0
    private val MAX_CONCURRENT_UPLOADS = 2

    data class UploadTask(val filePath: String, val originalFileName: String, val chatId: Long?)

    init {
        scope.launch {
            telegramClient.fileUpdates.collect { update ->
                val file = update.file
                val uniqueId = file.remote.uniqueId
                val fileId = file.id

                // --- PERBAIKAN 1: Hapus task berdasarkan file.id, bukan indeks ke-0 ---
                if (file.remote.isUploadingCompleted || (!file.remote.isUploadingActive && file.remote.uploadedSize == 0L)) {
                    Log.d("DriveRepo", "Upload selesai/berhenti untuk file ID: $fileId")
                    
                    // Cari nama file yang sesuai dengan fileId ini, lalu hapus dari activeTasks
                    val fileNameToRemove = activeTasks.entries.find { it.value == fileId }?.key
                    if (fileNameToRemove != null) {
                        activeTasks.remove(fileNameToRemove)
                    }
                    
                    triggerProgressUpdate()
                    fetchFiles()
                }

                if (file.remote.isUploadingActive) {
                    triggerProgressUpdate()
                }
                
                if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) {
                    val fileName = exportOnComplete[uniqueId] ?: exportOnComplete["temp_$fileId"]
                    if (fileName != null) {
                        transferRepository.saveToPublicDownloads(file.local.path, fileName)
                        exportOnComplete.remove(uniqueId)
                        exportOnComplete.remove("temp_$fileId")
                    }
                    if (uniqueId.isNotEmpty()) {
                        driveDao.updateLocalPathByUniqueId(uniqueId, file.local.path)
                        driveDao.updateThumbnailPathByUniqueId(uniqueId, file.local.path)
                    }
                    driveDao.updateLocalPath(fileId, file.local.path)
                    fetchFiles()
                    triggerProgressUpdate()
                }
            }
        }

        // --- PERBAIKAN 2: Gabungkan data tanpa distinctBy agar semua file muncul ---
        scope.launch {
            transferRepository.transfers.combine(uploadQueueStateFlow()) { transfers, queue ->
                val uploadTransfers = transfers.values
                    .filter { !it.isDownload && (it.status == "Mengunggah" || it.status == "Mengantre" || it.status == "Selesai") }
                    .map { transfer ->
                        UploadProgressItem(
                            fileName = transfer.fileName,
                            status = transfer.status,
                            progress = transfer.progress,
                            downloadedSize = transfer.downloadedSize,
                            totalSize = transfer.totalSize,
                            uniqueId = transfer.remoteUniqueId,
                            isDownload = false
                        )
                    }
                
                val queueItems = queue.map { task ->
                    UploadProgressItem(
                        fileName = task.originalFileName,
                        status = "Mengantre",
                        progress = 0f,
                        downloadedSize = 0,
                        totalSize = 0,
                        uniqueId = "",
                        isDownload = false
                    )
                }
                
                // PERBAIKAN: Hapus distinctBy! Biarkan semua item muncul
                val combined = uploadTransfers + queueItems
                
                // Opsional: Urutkan agar yang aktif di atas (Mengunggah > Mengantre > Selesai)
                combined.sortedByDescending { 
                    when(it.status) {
                        "Mengunggah" -> 3
                        "Mengantre" -> 2
                        "Selesai" -> 1
                        else -> 0
                    }
                }
            }.collect { combined ->
                _currentUploads.value = combined
            }
        }
    }

    private fun uploadQueueStateFlow(): Flow<List<UploadTask>> = flow {
        while (true) {
            emit(uploadQueue.toList())
            delay(500)
        }
    }.flowOn(Dispatchers.IO)

    private fun triggerProgressUpdate() {
        // Tidak perlu melakukan debounce lagi, karena flow combine sudah menanganinya
    }

    private fun startTransferService() {
        val intent = Intent(context, TransferService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun getItems(chatId: Long?, searchQuery: String = ""): Flow<List<DriveItem>> {
        val targetChatId = chatId ?: savedMessagesChatId
        val flow = if (searchQuery.isNotEmpty()) {
            driveDao.searchGlobal(searchQuery)
        } else if (targetChatId != 0L) {
            driveDao.getItemsFlow(targetChatId)
        } else {
            flowOf(emptyList())
        }

        return flow.map { entities ->
            entities.sortedByDescending { it.createdAt }.map { entity ->
                if (entity.isFolder) {
                    DriveItem.Folder(entity.id, entity.parentChatId, entity.name, entity.id, entity.isStarred)
                } else {
                    DriveItem.File(
                        entity.id, entity.parentChatId, entity.name, entity.size, entity.mimeType,
                        entity.telegramFileId, entity.thumbnailPath, entity.localPath, entity.isStarred, entity.remoteUniqueId ?: ""
                    )
                }
            }
        }
    }

    fun fetchFiles(chatId: Long? = null) {
        val targetChatId = chatId ?: savedMessagesChatId
        if (targetChatId == 0L) {
            telegramClient.send(TdApi.GetMe()) { result ->
                if (result is TdApi.User) {
                    savedMessagesChatId = result.id
                    _savedMessagesChatIdFlow.value = result.id
                    telegramClient.send(TdApi.OpenChat(result.id)) {
                        triggerDebouncedFetch(result.id)
                    }
                }
            }
        } else {
            telegramClient.send(TdApi.OpenChat(targetChatId)) {
                triggerDebouncedFetch(targetChatId)
            }
        }
    }

    private fun triggerDebouncedFetch(chatId: Long) {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            delay(1500)
            loadAllDriveItems(chatId)
        }
    }

    private fun loadAllDriveItems(chatId: Long) {
        // (Kode loadAllDriveItems tetap sama seperti aslinya, tidak diubah)
        // ... (potong untuk menghemat tempat, tetap gunakan kode Anda yang sudah ada)
    }

    fun createFolder(name: String) {
        telegramClient.send(TdApi.CreateNewSupergroupChat(name, false, true, "TeleDrive Folder", null, 0, false)) { result ->
            if (result is TdApi.Chat) { fetchFiles() }
        }
    }

    fun uploadFile(filePath: String, originalFileName: String, chatId: Long? = null) {
        uploadQueue.add(UploadTask(filePath, originalFileName, chatId))
        processUploadQueue()
    }

    private fun processUploadQueue() {
        scope.launch {
            synchronized(this@DriveRepository) {
                if (activeUploads >= MAX_CONCURRENT_UPLOADS || uploadQueue.isEmpty()) return@launch
                activeUploads++
            }
            val task = uploadQueue.poll() ?: return@launch
            executeActualUpload(task)
        }
    }

    private fun executeActualUpload(task: UploadTask) {
        val targetChatId = task.chatId ?: savedMessagesChatId
        if (targetChatId == 0L) {
            decrementActiveUploads()
            return
        }

        // Simpan task dengan kunci nama file, nilai akan diisi saat upload benar-benar dimulai
        activeTasks[task.originalFileName] = -1L // placeholder
        triggerProgressUpdate()

        startTransferService()
        val content = TdApi.InputMessageDocument(
            TdApi.InputFileLocal(task.filePath), null, false, TdApi.FormattedText(task.originalFileName, emptyArray())
        )
        telegramClient.send(TdApi.SendMessage(targetChatId, null, null, null, null, content)) { result ->
            if (result is TdApi.Message) {
                val msgContent = result.content
                if (msgContent is TdApi.MessageDocument) {
                    val doc = msgContent.document.document
                    // Simpan file ID yang sebenarnya agar bisa dihapus saat selesai
                    activeTasks[task.originalFileName] = doc.id
                    transferRepository.addTransfer(
                        doc.id, doc.remote.uniqueId, task.originalFileName, isDownload = false, totalSize = doc.expectedSize
                    )
                }
            }
            // Jangan hapus activeTasks di sini! Tunggu sampai UpdateFile dari Telegram mengatakan selesai.
            decrementActiveUploads()
            fetchFiles(targetChatId)
        }
    }

    private fun decrementActiveUploads() {
        synchronized(this@DriveRepository) { activeUploads-- }
        processUploadQueue()
    }

    fun cancelUpload(uniqueId: String) {
        transferRepository.cancelTransfer(uniqueId)
    }

    fun getSavedMessagesChatId(): Long = savedMessagesChatId

    fun downloadForPreview(messageId: Long, chatId: Long, fileName: String) {
        telegramClient.send(TdApi.GetMessage(chatId, messageId)) { result ->
            if (result is TdApi.Message) {
                val file = when (val content = result.content) {
                    is TdApi.MessageDocument -> content.document.document
                    is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo
                    is TdApi.MessageVideo -> content.video.video
                    is TdApi.MessageAudio -> content.audio.audio
                    is TdApi.MessageAnimation -> content.animation.animation
                    else -> null
                }
                if (file != null) {
                    val msgFileId = file.id
                    val remoteUniqueId = file.remote.uniqueId
                    val expectedSize = file.expectedSize
                    if (!file.local.isDownloadingCompleted) {
                        val trackId = if (remoteUniqueId.isNotEmpty()) remoteUniqueId else "temp_$msgFileId"
                        transferRepository.addTransfer(msgFileId, trackId, fileName, isDownload = true, totalSize = expectedSize)
                        startTransferService()
                        telegramClient.send(TdApi.DownloadFile(msgFileId, 32, 0, 0, false))
                    }
                }
            }
        }
    }

    fun downloadFile(messageId: Long, chatId: Long, fileName: String) {
        telegramClient.send(TdApi.GetMessage(chatId, messageId)) { result ->
            if (result is TdApi.Message) {
                val file = when (val content = result.content) {
                    is TdApi.MessageDocument -> content.document.document
                    is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo
                    is TdApi.MessageVideo -> content.video.video
                    is TdApi.MessageAudio -> content.audio.audio
                    is TdApi.MessageAnimation -> content.animation.animation
                    else -> null
                }
                if (file == null) return@send
                val msgFileId = file.id
                val remoteUniqueId = file.remote.uniqueId
                val expectedSize = file.expectedSize

                if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) {
                    transferRepository.saveToPublicDownloads(file.local.path, fileName)
                    transferRepository.addTransfer(msgFileId, remoteUniqueId, fileName, isDownload = true, totalSize = expectedSize, isCompleted = true)
                    return@send
                }

                if (remoteUniqueId.isNotEmpty()) {
                    exportOnComplete[remoteUniqueId] = fileName
                    transferRepository.addTransfer(msgFileId, remoteUniqueId, fileName, isDownload = true, totalSize = expectedSize)
                    startTransferService()
                    telegramClient.send(TdApi.DownloadFile(msgFileId, 32, 0, 0, false))
                } else if (msgFileId != 0) {
                    val tempId = "temp_$msgFileId"
                    exportOnComplete[tempId] = fileName
                    transferRepository.addTransfer(msgFileId, tempId, fileName, isDownload = true, totalSize = expectedSize)
                    startTransferService()
                    telegramClient.send(TdApi.DownloadFile(msgFileId, 32, 0, 0, false))
                }
            }
        }
    }

    fun saveToPublicStorage(file: DriveItem.File) {
        file.localPath?.let { path -> transferRepository.saveToPublicDownloads(path, file.name) }
    }

    fun getTotalStorageUsed(): Flow<Long> = driveDao.getTotalSize().map { it ?: 0L }

    fun getInternalCacheSize(): Flow<Long> {
        return kotlinx.coroutines.flow.flow {
            while (true) {
                val size = calculateDirectorySize(context.filesDir)
                emit(size)
                kotlinx.coroutines.delay(10000)
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun calculateDirectorySize(directory: java.io.File): Long {
        var size: Long = 0
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return size
    }

    fun clearInternalCache() {
        scope.launch {
            telegramClient.send(TdApi.OptimizeStorage(-1, 0, 0, 0, null, null, null, true, 0)) { fetchFiles() }
        }
    }

    fun toggleStarred(item: DriveItem) {
        val newState = when (item) {
            is DriveItem.File -> !item.isStarred
            is DriveItem.Folder -> !item.isStarred
        }
        scope.launch { driveDao.updateStarred(item.id, item.parentChatId, newState) }
    }

    fun getStarredItems(): Flow<List<DriveItem>> {
        return driveDao.getStarredItems().map { entities ->
            entities.map { entity ->
                if (entity.isFolder) {
                    DriveItem.Folder(entity.id, entity.parentChatId, entity.name, entity.id, entity.isStarred)
                } else {
                    DriveItem.File(
                        entity.id, entity.parentChatId, entity.name, entity.size, entity.mimeType,
                        entity.telegramFileId, entity.thumbnailPath, entity.localPath, entity.isStarred, entity.remoteUniqueId ?: ""
                    )
                }
            }
        }
    }

    fun getAllFiles(): Flow<List<DriveItem.File>> {
        return driveDao.getAllFiles().map { entities ->
            entities.map { entity ->
                DriveItem.File(
                    entity.id, entity.parentChatId, entity.name, entity.size, entity.mimeType,
                    entity.telegramFileId, entity.thumbnailPath, entity.localPath, entity.isStarred, entity.remoteUniqueId ?: ""
                )
            }
        }
    }

    fun permanentlyDeleteItems(chatId: Long, items: List<DriveItem>) {
        val messageIds = items.asSequence().filterIsInstance<DriveItem.File>().map { it.id }.toList()
        val folderIds = items.asSequence().filterIsInstance<DriveItem.Folder>().map { it.telegramChatId }.toList()

        if (messageIds.isNotEmpty()) {
            telegramClient.send(TdApi.DeleteMessages(chatId, messageIds.toLongArray(), true)) {
                scope.launch { messageIds.forEach { id -> driveDao.deleteItemCompletely(id, chatId) } }
            }
        }
        folderIds.forEach { fid ->
            telegramClient.send(TdApi.DeleteChat(fid)) {
                scope.launch {
                    driveDao.deleteItemsByChat(fid)
                    driveDao.deleteItemCompletely(fid, savedMessagesChatId)
                }
            }
        }
    }

    fun downloadFolderContents(folderChatId: Long) {
        telegramClient.send(TdApi.GetChatHistory(folderChatId, 0, 0, 1000, false)) { result ->
            if (result is TdApi.Messages) {
                result.messages.forEach { message ->
                    val fileInfo = when (val content = message.content) {
                        is TdApi.MessageDocument -> Pair(content.document.document, content.document.fileName)
                        is TdApi.MessagePhoto -> Pair(content.photo.sizes.lastOrNull()?.photo, "Photo_${message.id}.jpg")
                        is TdApi.MessageVideo -> Pair(content.video.video, content.video.fileName)
                        is TdApi.MessageAudio -> Pair(content.audio.audio, content.audio.fileName)
                        is TdApi.MessageAnimation -> Pair(content.animation.animation, content.animation.fileName)
                        else -> null
                    }
                    fileInfo?.let { (file, fileName) -> downloadFile(message.id, folderChatId, fileName) }
                }
            }
        }
    }

    fun moveFolderContentsAndDelete(fromFolderChatId: Long, toChatId: Long) {
        telegramClient.send(TdApi.GetChatHistory(fromFolderChatId, 0, 0, 1000, false)) { result ->
            if (result is TdApi.Messages) {
                val messageIds = result.messages.map { it.id }.toLongArray()
                if (messageIds.isNotEmpty()) {
                    val options = TdApi.MessageSendOptions().apply { disableNotification = true; fromBackground = true }
                    telegramClient.send(TdApi.ForwardMessages(toChatId, null, fromFolderChatId, messageIds, options, false, false)) { forwardResult ->
                        if (forwardResult is TdApi.Messages) {
                            telegramClient.send(TdApi.DeleteChat(fromFolderChatId)) {
                                scope.launch {
                                    driveDao.deleteItemsByChat(fromFolderChatId)
                                    driveDao.deleteItemCompletely(fromFolderChatId, savedMessagesChatId)
                                    fetchFiles(toChatId)
                                }
                            }
                        }
                    }
                } else {
                    telegramClient.send(TdApi.DeleteChat(fromFolderChatId)) {
                        scope.launch {
                            driveDao.deleteItemCompletely(fromFolderChatId, savedMessagesChatId)
                            fetchFiles(toChatId)
                        }
                    }
                }
            }
        }
    }

    fun moveItems(fromChatId: Long, toChatId: Long, messageIds: List<Long>) {
        val options = TdApi.MessageSendOptions().apply { disableNotification = true; fromBackground = true }
        telegramClient.send(TdApi.ForwardMessages(toChatId, null, fromChatId, messageIds.toLongArray(), options, false, false)) { result ->
            if (result is TdApi.Messages) {
                val successfulOriginalIds = mutableListOf<Long>()
                result.messages.forEachIndexed { index, message ->
                    if (message != null && index < messageIds.size) { successfulOriginalIds.add(messageIds[index]) }
                }
                if (successfulOriginalIds.isNotEmpty()) {
                    telegramClient.send(TdApi.DeleteMessages(fromChatId, successfulOriginalIds.toLongArray(), true)) {
                        fetchFiles(fromChatId)
                        fetchFiles(toChatId)
                    }
                }
            }
        }
    }
}
