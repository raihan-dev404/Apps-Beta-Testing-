package com.example.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val PREFS_NAME = "files_preferences"
private const val STORAGE_KEY = "selected_tree"
private const val FAVORITES_KEY = "saved_folders"
private const val TRASH_KEY = "trash_items"
private const val MAX_SCAN_DEPTH = 4
private const val MAX_SCAN_ITEMS = 300

private val FilesColors = lightColorScheme(
    primary = Color(0xFF965900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB5),
    onPrimaryContainer = Color(0xFF301900),
    secondary = Color(0xFF735A42),
    secondaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFFFFF8F1),
    surface = Color(0xFFFFF8F1),
    surfaceVariant = Color(0xFFF2E4D5),
    outline = Color(0xFF837568)
)

private enum class Page(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Browse("Browse", Icons.Default.FolderOpen),
    Categories("Categories", Icons.Default.Apps),
    Collections("Collections", Icons.Default.Star),
    Clean("Clean", Icons.Default.CleaningServices),
    Trash("Trash", Icons.Default.Delete),
    Settings("Settings", Icons.Default.Settings)
}

private enum class PickerPurpose { STORAGE, COPY, MOVE }

private data class TrashEntry(
    val id: String,
    val name: String,
    val size: Long,
    val originalStorage: String,
    val deletedAt: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = packageManager.hasSystemFeature("android.software.leanback")
        setContent { FilesApp(isTv = isTv) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesApp(isTv: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var rootUri by remember {
        mutableStateOf(prefs.getString(STORAGE_KEY, null)?.toUri())
    }
    val folderStack = remember { mutableStateListOf<DocumentFile>() }
    var entries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var scannedFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var page by remember { mutableStateOf(Page.Home) }
    var searchText by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var savedFolders by remember {
        mutableStateOf(prefs.getStringSet(FAVORITES_KEY, emptySet())?.toSet() ?: emptySet())
    }
    var trashEntries by remember { mutableStateOf(readTrash(prefs.getString(TRASH_KEY, "[]"))) }
    var pickerPurpose by remember { mutableStateOf(PickerPurpose.STORAGE) }
    var transferItem by remember { mutableStateOf<DocumentFile?>(null) }
    var pendingTrash by remember { mutableStateOf<DocumentFile?>(null) }
    var pendingPermanentDelete by remember { mutableStateOf<TrashEntry?>(null) }
    var pendingRename by remember { mutableStateOf<DocumentFile?>(null) }
    var renameText by remember { mutableStateOf("") }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.onFailure { error ->
                scope.launch { snackbar.showSnackbar("Folder access could not be saved: ${error.message ?: "permission denied"}") }
            }
            when (pickerPurpose) {
                PickerPurpose.STORAGE -> {
                    rootUri = uri
                    prefs.edit { putString(STORAGE_KEY, uri.toString()) }
                    page = Page.Browse
                }
                PickerPurpose.COPY, PickerPurpose.MOVE -> {
                    val item = transferItem
                    if (item != null) scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            transferFile(context, item, uri, pickerPurpose == PickerPurpose.MOVE)
                        }
                        result.onSuccess {
                            refreshToken++
                            snackbar.showSnackbar(if (pickerPurpose == PickerPurpose.MOVE) "File moved" else "File copied")
                        }.onFailure {
                            snackbar.showSnackbar("Could not transfer file: ${it.message ?: "storage provider refused the operation"}")
                        }
                    }
                    transferItem = null
                }
            }
        }
    }

    LaunchedEffect(rootUri) {
        folderStack.clear()
        val root = rootUri?.let { DocumentFile.fromTreeUri(context, it) }
        if (root != null) folderStack.add(root)
    }

    val currentFolder = folderStack.lastOrNull()
    LaunchedEffect(currentFolder, refreshToken) {
        entries = if (currentFolder == null) emptyList() else try {
            withContext(Dispatchers.IO) { currentFolder.listFiles().sortedWith(Comparator(::fileComparator)) }
        } catch (error: Exception) {
            snackbar.showSnackbar("Could not read this folder: ${error.message ?: "storage provider refused access"}")
            emptyList()
        }
    }
    LaunchedEffect(rootUri, refreshToken) {
        scannedFiles = if (rootUri == null) emptyList() else try {
            withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(context, rootUri!!)
                    ?: throw IOException("Selected storage is unavailable")
                scanFiles(root)
            }
        } catch (error: Exception) {
            snackbar.showSnackbar("Could not scan selected storage: ${error.message ?: "provider refused access"}")
            emptyList()
        }
    }

    MaterialTheme(colorScheme = FilesColors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Files", fontWeight = FontWeight.Bold)
                            Text(
                                currentFolder?.name ?: "Choose a folder to get started",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (page == Page.Browse && folderStack.size > 1) {
                            IconButton(onClick = { folderStack.removeAt(folderStack.lastIndex) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go up one folder")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchText = "" }) {
                            Icon(
                                if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searchVisible) "Close search" else "Search files"
                            )
                        }
                        IconButton(onClick = {
                            pickerPurpose = PickerPurpose.STORAGE
                            treePicker.launch(rootUri)
                        }) {
                            Icon(Icons.Default.Storage, contentDescription = "Choose storage folder")
                        }
                    }
                )
            },
            bottomBar = {
                if (!isTv) {
                    NavigationBar {
                        listOf(Page.Home, Page.Browse, Page.Categories, Page.Collections, Page.Settings).forEach { destination ->
                            NavigationBarItem(
                                selected = page == destination,
                                onClick = { page = destination },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.title) }
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Row(
                Modifier.fillMaxSize().padding(padding)
            ) {
                if (isTv) {
                    NavigationRailContent(page = page, onSelect = { page = it }, modifier = Modifier.width(208.dp))
                }
                Column(Modifier.fillMaxSize().weight(1f)) {
                    AnimatedVisibility(
                        visible = searchVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("Search this folder") },
                            singleLine = true
                        )
                    }
                    when (page) {
                        Page.Home -> HomePage(
                            hasStorage = currentFolder != null,
                            files = scannedFiles.sortedByDescending { it.lastModified() }.take(8),
                            onChooseStorage = {
                                pickerPurpose = PickerPurpose.STORAGE
                                treePicker.launch(rootUri)
                            },
                            onBrowse = { page = Page.Browse },
                            onCategories = { page = Page.Categories },
                            onOpenFile = { openFile(context, it, snackbar, scope) },
                            onOpenFolder = { folder ->
                                folderStack.add(folder)
                                page = Page.Browse
                            },
                            onAddFavorite = {
                                currentFolder?.let { folder ->
                                    savedFolders = savedFolders + folder.uri.toString()
                                    prefs.edit { putStringSet(FAVORITES_KEY, savedFolders) }
                                    scope.launch { snackbar.showSnackbar("Folder saved to Collections") }
                                }
                            }
                        )
                        Page.Browse -> {
                            val source = if (category == null) entries else scannedFiles
                            val visible = source.filter { matchesSearch(it, searchText) && matchesCategory(it, category) }
                            FileListPage(
                                title = currentFolder?.name ?: "Choose a storage folder",
                                category = category,
                                files = visible,
                                onOpen = { file ->
                                    if (file.isDirectory) folderStack.add(file)
                                    else openFile(context, file, snackbar, scope)
                                },
                                onShare = { shareFile(context, it, snackbar, scope) },
                                onRename = {
                                    pendingRename = it
                                    renameText = it.name.orEmpty()
                                },
                                onTrash = { pendingTrash = it },
                                onCopy = { item -> transferItem = item; pickerPurpose = PickerPurpose.COPY; treePicker.launch(null) },
                                onMove = { item -> transferItem = item; pickerPurpose = PickerPurpose.MOVE; treePicker.launch(null) },
                                onSaveFolder = { folder ->
                                    savedFolders = savedFolders + folder.uri.toString()
                                    prefs.edit { putStringSet(FAVORITES_KEY, savedFolders) }
                                    scope.launch { snackbar.showSnackbar("Folder saved to Collections") }
                                },
                                onRefresh = { refreshToken++ },
                                onClearCategory = { category = null },
                                onChooseStorage = {
                                    pickerPurpose = PickerPurpose.STORAGE
                                    treePicker.launch(rootUri)
                                }
                            )
                        }
                        Page.Categories -> CategoriesPage { selected ->
                            category = selected
                            page = Page.Browse
                        }
                        Page.Collections -> CollectionsPage(
                            savedUris = savedFolders,
                            rootUri = rootUri,
                            onChooseStorage = {
                                pickerPurpose = PickerPurpose.STORAGE
                                treePicker.launch(rootUri)
                            },
                            onRemove = { uri ->
                                savedFolders = savedFolders - uri
                                prefs.edit { putStringSet(FAVORITES_KEY, savedFolders) }
                            },
                            onOpen = { uri ->
                                val tree = rootUri
                                if (tree == null) {
                                    scope.launch { snackbar.showSnackbar("Choose the storage folder where this collection was saved") }
                                } else scope.launch {
                                    val path = withContext(Dispatchers.IO) {
                                        val root = DocumentFile.fromTreeUri(context, tree)
                                        if (root == null) null else findFolderPath(root, uri)
                                    }
                                    if (path == null) snackbar.showSnackbar("Folder not found. It may have been moved or access revoked.")
                                    else {
                                        folderStack.clear()
                                        folderStack.addAll(path)
                                        page = Page.Browse
                                    }
                                }
                            }
                        )
                        Page.Clean -> CleanPage(
                            candidates = scannedFiles.filter { it.length() >= 10L * 1024 * 1024 },
                            onReview = { item -> pendingTrash = item }
                        )
                        Page.Trash -> TrashPage(
                            entries = trashEntries,
                            onRestore = { entry ->
                                val root = rootUri
                                if (root == null) scope.launch { snackbar.showSnackbar("Choose a destination folder before restoring") }
                                else scope.launch {
                                    val result = withContext(Dispatchers.IO) { restoreTrash(context, entry, root) }
                                    result.onSuccess {
                                        trashEntries = readTrash(prefs.getString(TRASH_KEY, "[]"))
                                        refreshToken++
                                        snackbar.showSnackbar("Restored to ${DocumentFile.fromTreeUri(context, root)?.name ?: "selected storage"}")
                                    }.onFailure { snackbar.showSnackbar("Could not restore file: ${it.message}") }
                                }
                            },
                            onDeletePermanently = { entry ->
                                pendingPermanentDelete = entry
                            }
                        )
                        Page.Settings -> SettingsPage(
                            storageName = currentFolder?.name,
                            onChooseStorage = {
                                pickerPurpose = PickerPurpose.STORAGE
                                treePicker.launch(rootUri)
                            },
                            onOpenClean = { page = Page.Clean },
                            onOpenTrash = { page = Page.Trash },
                            onFeedback = { sendFeedback(context, snackbar, scope) }
                        )
                    }
                }
            }
        }
    }

    pendingTrash?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingTrash = null },
            title = { Text("Move to Trash?") },
            text = { Text("A recoverable copy of ${item.name} will be saved in this app before the original is removed. This uses space on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingTrash = null
                    val storage = rootUri
                    if (storage == null) scope.launch { snackbar.showSnackbar("Choose a storage folder first") }
                    else scope.launch {
                        val result = withContext(Dispatchers.IO) { trashFile(context, prefs, item, storage) }
                        result.onSuccess {
                            trashEntries = readTrash(prefs.getString(TRASH_KEY, "[]"))
                            refreshToken++
                            snackbar.showSnackbar("Moved to Trash")
                        }.onFailure { snackbar.showSnackbar("Could not move to Trash: ${it.message ?: "storage provider refused the operation"}") }
                    }
                }) { Text("Move to Trash") }
            },
            dismissButton = { TextButton(onClick = { pendingTrash = null }) { Text("Cancel") } }
        )
    }

    pendingRename?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    pendingRename = null
                    if (newName.isNotBlank()) {
                        scope.launch {
                            val renamed = withContext(Dispatchers.IO) { item.renameTo(newName) }
                            if (renamed) refreshToken++ else snackbar.showSnackbar("This storage provider could not rename the item")
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } }
        )
    }

    pendingPermanentDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingPermanentDelete = null },
            title = { Text("Delete permanently?") },
            text = { Text("${entry.name} will be permanently removed from Files Trash and cannot be restored.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPermanentDelete = null
                    scope.launch {
                        val file = File(context.filesDir, "trash/${entry.id}")
                        val deleted = withContext(Dispatchers.IO) { file.delete() }
                        if (!deleted && file.exists()) {
                            snackbar.showSnackbar("Could not remove the Trash copy")
                        } else {
                            trashEntries = trashEntries.filterNot { it.id == entry.id }
                            saveTrash(prefs, trashEntries)
                        }
                    }
                }) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { pendingPermanentDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NavigationRailContent(page: Page, onSelect: (Page) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
        LazyColumn(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("FILES", Modifier.padding(start = 16.dp, bottom = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            items(Page.entries) { destination ->
                NavigationItem(destination = destination, selected = page == destination, onClick = { onSelect(destination) })
            }
        }
    }
}

@Composable
private fun NavigationItem(destination: Page, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().focusable(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(destination.title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun HomePage(
    hasStorage: Boolean,
    files: List<DocumentFile>,
    onChooseStorage: () -> Unit,
    onBrowse: () -> Unit,
    onCategories: () -> Unit,
    onOpenFile: (DocumentFile) -> Unit,
    onOpenFolder: (DocumentFile) -> Unit,
    onAddFavorite: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text("Your files, in one place.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Browse storage, find something quickly, and keep folders close.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!hasStorage) {
            item {
                Card(onClick = onChooseStorage, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(36.dp))
                        Text("Choose a storage folder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Grant access to a folder, SD card, or USB drive using Android's folder picker.")
                        OutlinedButton(onClick = onChooseStorage) { Text("Choose folder") }
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Browse storage", "Folders and files", Icons.Default.Storage, onBrowse, Modifier.weight(1f))
                    SummaryCard("Categories", "Photos, video, audio…", Icons.Default.Apps, onCategories, Modifier.weight(1f))
                }
            }
            item {
                Card(onClick = onAddFavorite, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Save this folder", fontWeight = FontWeight.SemiBold)
                            Text("Add the current storage folder to Collections", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Add, contentDescription = "Save folder")
                    }
                }
            }
            item { Text("Recent in selected storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            if (files.isEmpty()) {
                item { Text("No files found yet. Recent items are discovered within the selected folder.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(files, key = { it.uri.toString() }) { file ->
                    FileRow(file = file, onClick = { if (file.isDirectory) onOpenFolder(file) else onOpenFile(file) }, onAction = {})
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileListPage(
    title: String,
    category: String?,
    files: List<DocumentFile>,
    onOpen: (DocumentFile) -> Unit,
    onShare: (DocumentFile) -> Unit,
    onRename: (DocumentFile) -> Unit,
    onTrash: (DocumentFile) -> Unit,
    onCopy: (DocumentFile) -> Unit,
    onMove: (DocumentFile) -> Unit,
    onSaveFolder: (DocumentFile) -> Unit,
    onRefresh: () -> Unit,
    onClearCategory: () -> Unit,
    onChooseStorage: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRefresh) { Icon(Icons.Default.AccessTime, contentDescription = "Refresh listing") }
            IconButton(onClick = onChooseStorage) { Icon(Icons.Default.Add, contentDescription = "Choose another folder") }
        }
        if (category != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$category in selected storage", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onClearCategory) { Text("Clear filter") }
            }
        }
        if (files.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Text("No matching items", style = MaterialTheme.typography.titleMedium)
                Text("Try another search or choose a storage folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    FileRow(file, onClick = { onOpen(file) }, onAction = { action ->
                        when (action) {
                            "share" -> onShare(file)
                            "rename" -> onRename(file)
                            "trash" -> onTrash(file)
                            "copy" -> onCopy(file)
                            "move" -> onMove(file)
                            "save" -> onSaveFolder(file)
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: DocumentFile, onClick: () -> Unit, onAction: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    FocusCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                fileIcon(file),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(file.name ?: "Unnamed item", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (file.isDirectory) "Folder" else "${formatBytes(file.length())}  •  ${formatDate(file.lastModified())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${file.name}") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (!file.isDirectory) {
                        DropdownMenuItem(text = { Text("Open") }, onClick = { expanded = false; onClick() })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { expanded = false; onAction("share") })
                        DropdownMenuItem(text = { Text("Copy to…") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { expanded = false; onAction("copy") })
                        DropdownMenuItem(text = { Text("Move to…") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) }, onClick = { expanded = false; onAction("move") })
                        DropdownMenuItem(text = { Text("Move to Trash") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { expanded = false; onAction("trash") })
                    } else {
                        DropdownMenuItem(text = { Text("Save folder") }, leadingIcon = { Icon(Icons.Default.Star, null) }, onClick = { expanded = false; onAction("save") })
                    }
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { expanded = false; onAction("rename") })
                }
            }
        }
    }
}

@Composable
private fun FocusCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) { content() }
}

@Composable
private fun CategoriesPage(onSelect: (String) -> Unit) {
    val categories = listOf(
        Triple("Images", Icons.Default.Image, "jpg • png • webp"),
        Triple("Videos", Icons.Default.VideoLibrary, "mp4 • mkv • mov"),
        Triple("Audio", Icons.Default.MusicNote, "mp3 • wav • flac"),
        Triple("Documents", Icons.Default.Description, "pdf • docx • txt"),
        Triple("Apps", Icons.Default.Apps, "APK packages")
    )
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Categories", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Find matching files within the selected folder tree.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(categories) { (name, icon, extensions) ->
            FocusCard(onClick = { onSelect(name) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(extensions, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionsPage(
    savedUris: Set<String>,
    rootUri: Uri?,
    onChooseStorage: () -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Collections", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Your saved folders. Re-select their storage if access has changed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (savedUris.isEmpty()) {
            item { Text("No saved folders yet. Save a folder from Browse or Home.") }
        } else {
            if (rootUri == null) item { OutlinedButton(onClick = onChooseStorage) { Text("Choose storage folder") } }
            items(savedUris.toList(), key = { it }) { uri ->
                FocusCard(onClick = { onOpen(uri) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(savedFolderName(uri), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onRemove(uri) }) { Icon(Icons.Default.Close, contentDescription = "Remove saved folder") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanPage(candidates: List<DocumentFile>, onReview: (DocumentFile) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Clean", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Review larger files before removing anything. Nothing is deleted automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Showing files of 10 MB or more from the selected folder tree.")
                }
            }
        }
        if (candidates.isEmpty()) item { Text("No large files found in the scanned folders.") }
        items(candidates.sortedByDescending { it.length() }, key = { it.uri.toString() }) { file ->
            FocusCard(onClick = { onReview(file) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(fileIcon(file), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name ?: "Unnamed item", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatBytes(file.length())} • Review before deleting", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onReview(file) }) { Text("Review") }
                }
            }
        }
    }
}

@Composable
private fun TrashPage(entries: List<TrashEntry>, onRestore: (TrashEntry) -> Unit, onDeletePermanently: (TrashEntry) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Trash", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Recoverable copies are stored privately by Files. Restore copies to the selected storage root.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entries.isEmpty()) item { Text("Trash is empty.") }
        items(entries, key = { it.id }) { entry ->
            FocusCard(onClick = { onRestore(entry) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatBytes(entry.size)} • ${formatDate(entry.deletedAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onRestore(entry) }) { Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore ${entry.name}") }
                    IconButton(onClick = { onDeletePermanently(entry) }) { Icon(Icons.Default.Close, contentDescription = "Permanently delete ${entry.name}") }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    storageName: String?,
    onChooseStorage: () -> Unit,
    onOpenClean: () -> Unit,
    onOpenTrash: () -> Unit,
    onFeedback: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selected storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(storageName ?: "Not selected")
                    OutlinedButton(onClick = onChooseStorage) { Text("Choose storage folder") }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Storage tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenClean) { Text("Clean") }
                        OutlinedButton(onClick = onOpenTrash) { Text("Trash") }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About storage access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Files only reads folders you choose. Android and each USB/SD provider decide which operations are available. No all-files permission is requested.")
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Help & feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("If a file cannot be opened, shared, renamed, or moved, the selected storage provider or installed apps may not support that action.")
                    OutlinedButton(onClick = onFeedback) { Text("Share feedback") }
                }
            }
        }
    }
}

private fun fileComparator(a: DocumentFile, b: DocumentFile): Int {
    if (a.isDirectory != b.isDirectory) return if (a.isDirectory) -1 else 1
    return (a.name ?: "").compareTo(b.name ?: "", ignoreCase = true)
}

private fun scanFiles(root: DocumentFile): List<DocumentFile> {
    val result = mutableListOf<DocumentFile>()
    fun visit(folder: DocumentFile, depth: Int) {
        if (depth > MAX_SCAN_DEPTH || result.size >= MAX_SCAN_ITEMS) return
        folder.listFiles().sortedWith(Comparator(::fileComparator)).forEach { child ->
            if (result.size >= MAX_SCAN_ITEMS) return
            if (child.isDirectory) visit(child, depth + 1) else result.add(child)
        }
    }
    visit(root, 0)
    return result
}

private fun matchesSearch(file: DocumentFile, query: String): Boolean =
    query.isBlank() || (file.name ?: "").contains(query.trim(), ignoreCase = true)

private fun matchesCategory(file: DocumentFile, category: String?): Boolean {
    if (category == null) return true
    val ext = file.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when (category) {
        "Images" -> ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif")
        "Videos" -> ext in setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v")
        "Audio" -> ext in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus")
        "Documents" -> ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "epub")
        "Apps" -> ext == "apk"
        else -> true
    }
}

private fun fileIcon(file: DocumentFile): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder
    return when (file.name?.substringAfterLast('.', "")?.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic" -> Icons.Default.Image
        "mp4", "mkv", "mov", "avi", "webm" -> Icons.Default.VideoLibrary
        "mp3", "wav", "flac", "aac", "ogg", "m4a" -> Icons.Default.MusicNote
        "apk" -> Icons.Default.Apps
        "pdf", "doc", "docx", "txt", "csv", "xls", "xlsx", "ppt", "pptx" -> Icons.Default.Description
        else -> Icons.Default.Description
    }
}

private fun openFile(context: Context, file: DocumentFile, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val extension = file.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    val mime = if (extension == "apk") "application/vnd.android.package-archive"
    else MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        scope.launch { snackbar.showSnackbar("No installed app can open this file") }
    } catch (error: SecurityException) {
        scope.launch { snackbar.showSnackbar("Storage provider did not grant access: ${error.message ?: "permission denied"}") }
    }
}

private fun shareFile(context: Context, file: DocumentFile, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val extension = file.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, file.uri)
        clipData = android.content.ClipData.newUri(context.contentResolver, file.name, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, "Share ${file.name ?: "file"}"))
    } catch (error: Exception) {
        scope.launch { snackbar.showSnackbar("Could not share file: ${error.message ?: "no sharing app available"}") }
    }
}

private fun sendFeedback(context: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Files app feedback")
        putExtra(Intent.EXTRA_TEXT, "Feedback about the Files app:")
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share feedback"))
    } catch (error: ActivityNotFoundException) {
        scope.launch { snackbar.showSnackbar("No app is available to share feedback") }
    }
}

private fun transferFile(context: Context, source: DocumentFile, destinationTreeUri: Uri, move: Boolean): Result<Unit> = runCatching {
    if (source.isDirectory) throw IOException("Only individual files can be copied or moved")
    val destination = DocumentFile.fromTreeUri(context, destinationTreeUri)
        ?: throw IOException("Destination folder is unavailable")
    val name = uniqueName(destination, source.name ?: "file")
    val target = destination.createFile(source.type ?: "application/octet-stream", name)
        ?: throw IOException("Destination provider could not create the file")
    try {
        val input = context.contentResolver.openInputStream(source.uri) ?: throw IOException("Source file could not be opened")
        val output = context.contentResolver.openOutputStream(target.uri) ?: throw IOException("Destination file could not be opened")
        input.use { sourceStream -> output.use { destinationStream -> sourceStream.copyTo(destinationStream) } }
    } catch (error: Exception) {
        target.delete()
        throw error
    }
    if (move && !source.delete()) {
        throw IOException("Copied the file, but the provider would not remove the original")
    }
}

private fun uniqueName(folder: DocumentFile, requested: String): String {
    if (folder.findFile(requested) == null) return requested
    val dot = requested.lastIndexOf('.')
    val stem = if (dot > 0) requested.substring(0, dot) else requested
    val suffix = if (dot > 0) requested.substring(dot) else ""
    var index = 1
    while (folder.findFile("$stem ($index)$suffix") != null) index++
    return "$stem ($index)$suffix"
}

private fun trashFile(context: Context, prefs: android.content.SharedPreferences, source: DocumentFile, rootUri: Uri): Result<Unit> = runCatching {
    if (source.isDirectory) throw IOException("Folders cannot be moved to Trash yet")
    val id = UUID.randomUUID().toString()
    val directory = File(context.filesDir, "trash")
    if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create private Trash storage")
    val backup = File(directory, id)
    val input = context.contentResolver.openInputStream(source.uri) ?: throw IOException("Source file could not be read")
    try {
        input.use { sourceStream -> backup.outputStream().use { destination -> sourceStream.copyTo(destination) } }
    } catch (error: Exception) {
        backup.delete()
        throw error
    }
    val entry = TrashEntry(id, source.name ?: "file", source.length(), rootUri.toString(), System.currentTimeMillis())
    val current = readTrash(prefs.getString(TRASH_KEY, "[]"))
    saveTrash(prefs, current + entry)
    if (!source.delete()) {
        backup.delete()
        saveTrash(prefs, current)
        throw IOException("The provider would not remove the original file")
    }
}

private fun restoreTrash(context: Context, entry: TrashEntry, destinationTreeUri: Uri): Result<Unit> = runCatching {
    val backup = File(context.filesDir, "trash/${entry.id}")
    if (!backup.exists()) throw IOException("Trash copy is missing")
    val destination = DocumentFile.fromTreeUri(context, destinationTreeUri)
        ?: throw IOException("Destination folder is unavailable")
    val target = destination.createFile(
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(entry.name.substringAfterLast('.', "")) ?: "application/octet-stream",
        uniqueName(destination, entry.name)
    ) ?: throw IOException("Destination provider could not create the restored file")
    try {
        backup.inputStream().use { input -> context.contentResolver.openOutputStream(target.uri)?.use { output ->
            input.copyTo(output)
        } ?: throw IOException("Destination file could not be opened") }
    } catch (error: Exception) {
        target.delete()
        throw error
    }
    backup.delete()
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    saveTrash(prefs, readTrash(prefs.getString(TRASH_KEY, "[]")).filterNot { it.id == entry.id })
}

private fun readTrash(raw: String?): List<TrashEntry> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        TrashEntry(
            id = item.getString("id"),
            name = item.getString("name"),
            size = item.optLong("size"),
            originalStorage = item.optString("storage"),
            deletedAt = item.optLong("deletedAt")
        )
    }
}.getOrDefault(emptyList())

private fun saveTrash(prefs: android.content.SharedPreferences, entries: List<TrashEntry>) {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(
            JSONObject()
                .put("id", entry.id)
                .put("name", entry.name)
                .put("size", entry.size)
                .put("storage", entry.originalStorage)
                .put("deletedAt", entry.deletedAt)
        )
    }
    prefs.edit { putString(TRASH_KEY, array.toString()) }
}

private fun findFolderPath(root: DocumentFile, uri: String): List<DocumentFile>? {
    val path = mutableListOf<DocumentFile>()
    fun visit(folder: DocumentFile, depth: Int): Boolean {
        path.add(folder)
        if (folder.uri.toString() == uri) return true
        if (depth < MAX_SCAN_DEPTH) {
            folder.listFiles().filter { it.isDirectory }.forEach { child ->
                if (visit(child, depth + 1)) return true
            }
        }
        path.removeAt(path.lastIndex)
        return false
    }
    return if (visit(root, 0)) path.toList() else null
}

private fun savedFolderName(uri: String): String =
    uri.toUri().lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.ifBlank { null } ?: "Saved folder"

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

private fun formatDate(timestamp: Long): String = if (timestamp <= 0L) "Date unavailable"
else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
