package com.photoframe.tv.ui.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.photoframe.tv.di.AppGraph
import com.photoframe.tv.drive.DriveRepository
import com.photoframe.tv.drive.Photo
import com.photoframe.tv.ui.slideshow.simpleFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhoneViewModel(private val repo: DriveRepository) : ViewModel() {
    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos = _photos.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun refresh() = viewModelScope.launch { reload(setBusy = true) }

    fun upload(items: List<Triple<String, String, ByteArray>>) = viewModelScope.launch {
        _busy.value = true
        items.forEach { (name, mime, bytes) -> runCatching { repo.uploadPhoto(name, mime, bytes) } }
        reload(setBusy = false)
    }

    fun delete(id: String) = viewModelScope.launch {
        _busy.value = true
        runCatching { repo.deletePhoto(id) }
        reload(setBusy = false)
    }

    private suspend fun reload(setBusy: Boolean) {
        if (setBusy) _busy.value = true
        _photos.value = runCatching { repo.listPhotos(null, false) }.getOrDefault(_photos.value)
        _busy.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneGalleryScreen(graph: AppGraph, onDisconnect: () -> Unit) {
    val vm: PhoneViewModel = viewModel(factory = simpleFactory { PhoneViewModel(graph.driveRepository) })
    LaunchedEffect(Unit) { vm.refresh() }
    val photos by vm.photos.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var viewing by remember { mutableStateOf<Photo?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val items = withContext(Dispatchers.IO) { uris.mapNotNull { readUpload(context, it) } }
                vm.upload(items)
            }
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val url = result.contents
        if (!url.isNullOrBlank()) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Frame") },
                actions = {
                    IconButton(onClick = {
                        scanner.launch(
                            ScanOptions().setOrientationLocked(false)
                                .setPrompt("Scan the code on your TV to pair it")
                                .setBeepEnabled(false),
                        )
                    }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Pair TV") }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            onClick = { menuOpen = false; onDisconnect() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Icon(Icons.Filled.Add, contentDescription = "Add photos") }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (photos.isEmpty() && !busy) {
                Text(
                    "No photos yet.\nTap + to add photos to your frame.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(photo.url).size(400).build(),
                            imageLoader = graph.imageLoader,
                            contentDescription = photo.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clickable { viewing = photo },
                        )
                    }
                }
            }
        }
    }

    viewing?.let { photo ->
        Dialog(onDismissRequest = { viewing = null }) {
            Box(Modifier.fillMaxSize().background(Color(0xE6000000))) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(photo.url).build(),
                    imageLoader = graph.imageLoader,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = { vm.delete(photo.id); viewing = null }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("  Delete", color = Color.White)
                    }
                    TextButton(onClick = { viewing = null }) { Text("Close", color = Color.White) }
                }
            }
        }
    }
}

/** Read an image's bytes, mime, and display name from a content URI (for uploading). */
private fun readUpload(context: Context, uri: Uri): Triple<String, String, ByteArray>? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "photo_${System.currentTimeMillis()}.jpg"
    return Triple(name, mime, bytes)
}
