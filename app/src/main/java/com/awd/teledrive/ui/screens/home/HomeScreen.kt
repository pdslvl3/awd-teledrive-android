package com.awd.teledrive.ui.screens.home

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.awd.teledrive.R
import com.awd.teledrive.core.ConnectivityObserver
import com.awd.teledrive.domain.model.DriveItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.awd.teledrive.ui.common.FileUiUtils
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

import androidx.compose.ui.tooling.preview.Preview
import com.awd.teledrive.ui.theme.TeledriveTheme
import com.awd.teledrive.data.repository.UploadProgressItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTransfers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPreview: (DriveItem.File) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val totalStorageUsed by viewModel.totalStorageUsed.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName by viewModel.currentFolderName.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val connectivityStatus by viewModel.connectivityStatus.collectAsState()
    val duplicateToConfirm by viewModel.duplicateToConfirm.collectAsState()
    val currentUploads by viewModel.currentUploads.collectAsState()

    HomeScreenContent(
        driveItems = items,
        totalStorageUsed = totalStorageUsed,
        searchQuery = searchQuery,
        sortOrder = sortOrder,
        filterType = filterType,
        isRefreshing = isRefreshing,
        isInitialLoading = isInitialLoading,
        currentFolderId = currentFolderId,
        currentFolderName = currentFolderName,
        isGridView = isGridView,
        connectivityStatus = connectivityStatus,
        duplicateToConfirm = duplicateToConfirm,
        currentUploads = currentUploads,
        onConfirmSkip = viewModel::confirmSkip,
        onConfirmOverwrite = viewModel::confirmOverwrite,
        onCancelUpload = viewModel::cancelUpload,  // tambahan
        onNavigateToTransfers = onNavigateToTransfers,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToPreview = onNavigateToPreview,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleViewMode = viewModel::toggleViewMode,
        onSetSortOrder = viewModel::setSortOrder,
        onSetFilterType = viewModel::setFilterType,
        onCreateFolder = viewModel::createFolder,
        onUploadFile = viewModel::uploadFile,
        onDeleteItems = viewModel::deleteItems,
        onMoveItems = viewModel::moveItems,
        onMoveFolderContents = viewModel::moveFolderContentsAndDelete,
        onDownloadFile = viewModel::downloadFile,
        onDownloadFolderContents = viewModel::downloadFolderContents,
        onToggleStarred = viewModel::toggleStarred,
        onNavigateToFolder = viewModel::navigateToFolder,
        onNavigateBack = viewModel::navigateBack,
        onRefresh = viewModel::fetchItems,
        onLogout = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    driveItems: List<DriveItem>,
    totalStorageUsed: Long,
    searchQuery: String,
    sortOrder: SortOrder,
    filterType: FilterType,
    isRefreshing: Boolean,
    isInitialLoading: Boolean,
    currentFolderId: Long?,
    currentFolderName: String,
    isGridView: Boolean,
    connectivityStatus: ConnectivityObserver.Status,
    duplicateToConfirm: DuplicateUploadTask?,
    currentUploads: List<UploadProgressItem>,
    onConfirmSkip: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onCancelUpload: (String) -> Unit, // tambahan
    onNavigateToTransfers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPreview: (DriveItem.File) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onSetFilterType: (FilterType) -> Unit,
    onCreateFolder: (String) -> Unit,
    onUploadFile: (String, String) -> Unit,
    onDeleteItems: (List<DriveItem>) -> Unit,
    onMoveItems: (Set<Long>, Long) -> Unit,
    onMoveFolderContents: (Long, Long) -> Unit,
    onDownloadFile: (Long, Long, String) -> Unit,
    onDownloadFolderContents: (Long) -> Unit,
    onToggleStarred: (DriveItem) -> Unit,
    onNavigateToFolder: (Long?, String) -> Unit,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showNewSheet by remember { mutableStateOf(false) }

    val isOffline = connectivityStatus == ConnectivityObserver.Status.Unavailable || 
                    connectivityStatus == ConnectivityObserver.Status.Lost

    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedItems.isNotEmpty()
    var showFolderDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<List<DriveItem>?>(null) }
    var folderToMove by remember { mutableStateOf<DriveItem.Folder?>(null) }
    var folderToDownload by remember { mutableStateOf<DriveItem.Folder?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Picker file dan kamera (sama seperti asli)
    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "Sedang memproses berkas, mohon tunggu...", Toast.LENGTH_SHORT).show()
            scope.launch(Dispatchers.IO) {
                uris.forEach { uri ->
                    try {
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        val fileName = cursor?.use { c ->
                            val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            c.moveToFirst()
                            c.getString(nameIndex)
                        } ?: "file_${System.currentTimeMillis()}"

                        val inputStream = context.contentResolver.openInputStream(uri)
                        val tempFile = File(context.cacheDir, fileName)
                        inputStream?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            onUploadFile(tempFile.absolutePath, fileName)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Gagal memproses berkas raksasa: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val fileName = "IMG_${System.currentTimeMillis()}.jpg"
                    val file = File(context.cacheDir, fileName)
                    FileOutputStream(file).use { out ->
                        it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    withContext(Dispatchers.Main) {
                        onUploadFile(file.absolutePath, fileName)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal memproses foto", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val pressBackMsg = stringResource(R.string.press_back_again)
    var backPressedOnce by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            showNewSheet -> showNewSheet = false
            isSearchActive -> isSearchActive = false
            isSelectionMode -> selectedItems = emptySet()
            currentFolderId != null -> onNavigateBack()
            else -> {
                if (backPressedOnce) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    backPressedOnce = true
                    Toast.makeText(context, pressBackMsg, Toast.LENGTH_SHORT).show()
                    scope.launch {
                        delay(2.seconds)
                        backPressedOnce = false
                    }
                }
            }
        }
    }

    // Dialog-dialog (sama seperti asli, tidak diubah)

    // --- PERUBAHAN: Card upload dengan progres
    Scaffold(
        topBar = { /* ... sama seperti asli ... */ },
        floatingActionButton = { /* ... sama ... */ }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isInitialLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                
                    if (isOffline) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.offline_msg), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // --- PERUBAHAN: Card upload dengan progres
                    if (currentUploads.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload, 
                                        contentDescription = null, 
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Unggahan (${currentUploads.size} berkas)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 200.dp)
                                ) {
                                    items(currentUploads) { upload ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = upload.fileName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                if (upload.totalSize > 0) {
                                                    val sizeText = formatSize(upload.downloadedSize) + " / " + formatSize(upload.totalSize)
                                                    Text(
                                                        text = "$sizeText (${(upload.progress * 100).toInt()}%)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                } else {
                                                    Text(
                                                        text = upload.status,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                                LinearProgressIndicator(
                                                    progress = { upload.progress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            if (upload.status == "Mengunggah" || upload.status == "Mengantre") {
                                                IconButton(
                                                    onClick = { onCancelUpload(upload.uniqueId) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = stringResource(R.string.cancel),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    // ... (sisanya: daftar item, sama seperti asli)
                    if (driveItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isEmpty()) stringResource(R.string.drive_empty) else stringResource(R.string.no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(driveItems) { item ->
                                    val isSelected = selectedItems.contains(item.id)
                                    DriveGridItem(
                                        item = item,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                val hasFiles = driveItems.filter { it.id in selectedItems }.any { it is DriveItem.File }
                                                if (hasFiles && item is DriveItem.Folder) {
                                                    Toast.makeText(context, "Cannot select folders when files are selected", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                                                }
                                            } else {
                                                if (item is DriveItem.File) onNavigateToPreview(item)
                                                else if (item is DriveItem.Folder) onNavigateToFolder(item.telegramChatId, item.name)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                selectedItems = setOf(item.id)
                                            }
                                        },
                                        onStarClick = { onToggleStarred(item) },
                                        onDownloadClick = {
                                            if (item is DriveItem.File) {
                                                onDownloadFile(item.id, item.parentChatId, item.name)
                                            } else if (item is DriveItem.Folder) {
                                                folderToDownload = item
                                            }
                                        },
                                        onMoveClick = {
                                            selectedItems = setOf(item.id)
                                            showMoveDialog = true
                                        },
                                        onDeleteClick = {
                                            showDeleteConfirm = listOf(item)
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(driveItems) { item ->
                                    val isSelected = selectedItems.contains(item.id)
                                    DriveListItem(
                                        item = item,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                val hasFiles = driveItems.filter { it.id in selectedItems }.any { it is DriveItem.File }
                                                if (hasFiles && item is DriveItem.Folder) {
                                                    Toast.makeText(context, "Cannot select folders when files are selected", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                                                }
                                            } else {
                                                if (item is DriveItem.File) onNavigateToPreview(item)
                                                else if (item is DriveItem.Folder) onNavigateToFolder(item.telegramChatId, item.name)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                selectedItems = setOf(item.id)
                                            }
                                        },
                                        onStarClick = { onToggleStarred(item) },
                                        onDownloadClick = {
                                            if (item is DriveItem.File) {
                                                onDownloadFile(item.id, item.parentChatId, item.name)
                                            } else if (item is DriveItem.Folder) {
                                                folderToDownload = item
                                            }
                                        },
                                        onMoveClick = {
                                            selectedItems = setOf(item.id)
                                            showMoveDialog = true
                                        },
                                        onDeleteClick = {
                                            showDeleteConfirm = listOf(item)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Fungsi-fungsi helper (DriveListItem, DriveGridItem, InfoDialog, formatSize) tetap sama seperti asli.
// Untuk menghemat tempat, saya tidak menulis ulang semua, tetapi di kode akhir Anda harus menyertakannya.
                    if (driveItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isEmpty()) stringResource(R.string.drive_empty) else stringResource(R.string.no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(driveItems) { item ->
                                    val isSelected = selectedItems.contains(item.id)
                                    DriveGridItem(
                                        item = item,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                val hasFiles = driveItems.filter { it.id in selectedItems }.any { it is DriveItem.File }
                                                if (hasFiles && item is DriveItem.Folder) {
                                                    Toast.makeText(context, "Cannot select folders when files are selected", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                                                }
                                            } else {
                                                if (item is DriveItem.File) onNavigateToPreview(item)
                                                else if (item is DriveItem.Folder) onNavigateToFolder(item.telegramChatId, item.name)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                selectedItems = setOf(item.id)
                                            }
                                        },
                                        onStarClick = { onToggleStarred(item) },
                                        onDownloadClick = {
                                            if (item is DriveItem.File) {
                                                onDownloadFile(item.id, item.parentChatId, item.name)
                                            } else if (item is DriveItem.Folder) {
                                                folderToDownload = item
                                            }
                                        },
                                        onMoveClick = {
                                            selectedItems = setOf(item.id)
                                            showMoveDialog = true
                                        },
                                        onDeleteClick = {
                                            showDeleteConfirm = listOf(item)
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(driveItems) { item ->
                                    val isSelected = selectedItems.contains(item.id)
                                    DriveListItem(
                                        item = item,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                val hasFiles = driveItems.filter { it.id in selectedItems }.any { it is DriveItem.File }
                                                if (hasFiles && item is DriveItem.Folder) {
                                                    Toast.makeText(context, "Cannot select folders when files are selected", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                                                }
                                            } else {
                                                if (item is DriveItem.File) onNavigateToPreview(item)
                                                else if (item is DriveItem.Folder) onNavigateToFolder(item.telegramChatId, item.name)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                selectedItems = setOf(item.id)
                                            }
                                        },
                                        onStarClick = { onToggleStarred(item) },
                                        onDownloadClick = {
                                            if (item is DriveItem.File) {
                                                onDownloadFile(item.id, item.parentChatId, item.name)
                                            } else if (item is DriveItem.Folder) {
                                                folderToDownload = item
                                            }
                                        },
                                        onMoveClick = {
                                            selectedItems = setOf(item.id)
                                            showMoveDialog = true
                                        },
                                        onDeleteClick = {
                                            showDeleteConfirm = listOf(item)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    TeledriveTheme {
        HomeScreenContent(
            driveItems = listOf(
                DriveItem.Folder(id = 1L, parentChatId = 0L, name = "Documents", telegramChatId = 123456L, isStarred = false),
                DriveItem.File(id = 2L, parentChatId = 0L, name = "image.jpg", size = 1024 * 1024, mimeType = "image/jpeg", telegramFileId = 1, thumbnailPath = null, localPath = null, isStarred = false)
            ),
            totalStorageUsed = 1024 * 1024,
            searchQuery = "",
            sortOrder = SortOrder.NAME,
            filterType = FilterType.ALL,
            isRefreshing = false,
            isInitialLoading = false,
            currentFolderId = null,
            currentFolderName = "My Drive",
            isGridView = false,
            connectivityStatus = ConnectivityObserver.Status.Available,
            duplicateToConfirm = null,
            currentUploads = emptyList(),
            onConfirmSkip = {},
            onConfirmOverwrite = {},
            onNavigateToTransfers = {},
            onNavigateToSettings = {},
            onNavigateToPreview = { _ -> },
            onSearchQueryChange = { _ -> },
            onToggleViewMode = {},
            onSetSortOrder = { _ -> },
            onSetFilterType = { _ -> },
            onCreateFolder = { _ -> },
            onUploadFile = { _, _ -> },
            onDeleteItems = { _ -> },
            onMoveItems = { _, _ -> },
            onMoveFolderContents = { _, _ -> },
            onDownloadFile = { _, _, _ -> },
            onDownloadFolderContents = { _ -> },
            onToggleStarred = { _ -> },
            onNavigateToFolder = { _, _ -> },
            onNavigateBack = {},
            onRefresh = {},
            onLogout = {}
        )
    }
}

@Composable
fun NewActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DriveListItem(
    item: DriveItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStarClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMoveClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (item is DriveItem.File) {
                Text(formatSize(item.size), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.folder), style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            val (icon, color) = FileUiUtils.getFileIconAndColor(item)
            Surface(
                color = if (item is DriveItem.Folder) MaterialTheme.colorScheme.primaryContainer else color.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val thumbnailModel = if (item is DriveItem.File) {
                        item.localPath ?: item.thumbnailPath
                    } else null

                    if (thumbnailModel != null) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                            AsyncImage(
                                model = thumbnailModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                var showItemMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showItemMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                
                var showInfoDialog by remember { mutableStateOf(false) }
                
                DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (item.isStarred) stringResource(R.string.remove_star) else stringResource(R.string.add_star)) },
                        onClick = { 
                            onStarClick()
                            showItemMenu = false 
                        },
                        leadingIcon = { 
                            Icon(
                                imageVector = if (item.isStarred) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = null,
                                tint = if (item.isStarred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.info)) },
                        onClick = { showInfoDialog = true; showItemMenu = false },
                        leadingIcon = { Icon(Icons.Default.Info, null) }
                    )

                    if (item is DriveItem.File) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download)) },
                            onClick = { 
                                onDownloadClick()
                                showItemMenu = false 
                            },
                            leadingIcon = { Icon(Icons.Default.Download, null) }
                        )
                    } else if (item is DriveItem.Folder) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_contents)) },
                            onClick = { 
                                onDownloadClick()
                                showItemMenu = false 
                            },
                            leadingIcon = { Icon(Icons.Default.Download, null) }
                        )
                    }

                    if (item !is DriveItem.Folder) {
                        onMoveClick?.let {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move)) },
                                onClick = {
                                    it()
                                    showItemMenu = false
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) }
                            )
                        }
                    }

                    onDeleteClick?.let {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                it()
                                showItemMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
                
                if (showInfoDialog) {
                    InfoDialog(item = item, onDismiss = { showInfoDialog = false })
                }
            }
        },
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DriveGridItem(
    item: DriveItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStarClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMoveClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    var showItemMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Box {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val (icon, color) = FileUiUtils.getFileIconAndColor(item)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp)
                        .background(color.copy(alpha = 0.1f), MaterialTheme.shapes.small)
                ) {
                    val thumbnailModel = if (item is DriveItem.File) {
                        item.localPath ?: item.thumbnailPath
                    } else null

                    if (thumbnailModel != null) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = color
                            )
                            AsyncImage(
                                model = thumbnailModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().background(Color.Transparent),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = color
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { showItemMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (item.isStarred) stringResource(R.string.remove_star) else stringResource(R.string.add_star)) },
                                onClick = { onStarClick(); showItemMenu = false },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = if (item.isStarred) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        contentDescription = null,
                                        tint = if (item.isStarred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.info)) },
                                onClick = { showInfoDialog = true; showItemMenu = false },
                                leadingIcon = { Icon(Icons.Default.Info, null) }
                            )
                            if (item is DriveItem.File) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.download)) },
                                    onClick = { onDownloadClick(); showItemMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Download, null) }
                                )
                            } else if (item is DriveItem.Folder) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.download_contents)) },
                                    onClick = { onDownloadClick(); showItemMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Download, null) }
                                )
                            }
                            if (item !is DriveItem.Folder) {
                                onMoveClick?.let {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.move)) },
                                        onClick = { it(); showItemMenu = false },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) }
                                    )
                                }
                            }
                            onDeleteClick?.let {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = { it(); showItemMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
        }
    }
    
    if (showInfoDialog) {
        InfoDialog(item = item, onDismiss = { showInfoDialog = false })
    }
}

@Composable
fun InfoDialog(item: DriveItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.name_label, item.name), style = MaterialTheme.typography.bodyMedium)
                if (item is DriveItem.File) {
                    Text(stringResource(R.string.size_label, formatSize(item.size)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.type_label, item.mimeType), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(
                            R.string.status_label, 
                            if (item.localPath != null) stringResource(R.string.available_offline) else stringResource(R.string.cloud_only)
                        ), 
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(stringResource(R.string.type_label, stringResource(R.string.folder)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
