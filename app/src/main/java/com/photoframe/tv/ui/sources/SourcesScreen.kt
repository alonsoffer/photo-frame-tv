package com.photoframe.tv.ui.sources

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframe.tv.R
import com.photoframe.tv.data.SourceEntry
import com.photoframe.tv.data.SourceType
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

/** Derive a short display name from a SAF tree URI (e.g. "primary:DCIM/Frame" -> "Frame"). */
private fun folderNameFromTree(uri: Uri): String {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return ""
    return docId.substringAfterLast('/').substringAfterLast(':').ifBlank { docId }
}

/**
 * Manage the photo sources the slideshow pools from. Add local/USB folders (Storage Access
 * Framework) and/or Google Drive; remove any; start the slideshow once at least one exists.
 * Fully D-pad operable. Focus scaling is pinned to 1f so wide rows never overflow the screen.
 */
@Composable
fun SourcesScreen(
    sources: List<SourceEntry>,
    onAddLocal: (treeUri: String, name: String) -> Unit,
    onAddDrive: () -> Unit,
    onRemove: (id: String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onAddLocal(uri.toString(), folderNameFromTree(uri))
        }
    }

    val hasDrive = sources.any { it.type == SourceType.DRIVE }
    val noScale = ButtonDefaults.scale(focusedScale = 1f)
    val localLabel = stringResource(R.string.type_local)
    val driveLabel = stringResource(R.string.type_drive)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == Key.Back) { onBack(); true } else false
            }
            .padding(horizontal = 48.dp, vertical = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.sources_title),
            color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.sources_subtitle),
            color = Color(0xFFB0B8C4), fontSize = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Spacer(Modifier.height(4.dp))

        if (sources.isEmpty()) {
            Text(stringResource(R.string.sources_none), color = Color(0xFF8A94A6), fontSize = 15.sp)
        } else {
            sources.forEach { src ->
                val typeLabel = if (src.type == SourceType.LOCAL) localLabel else driveLabel
                val title = src.name.ifBlank { typeLabel }
                Button(
                    onClick = { onRemove(src.id) },
                    scale = noScale,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                ) {
                    Text("✕   $title   ·   $typeLabel", fontSize = 18.sp)
                }
            }
            Text(stringResource(R.string.sources_remove_hint), color = Color(0xFF8A94A6), fontSize = 13.sp)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { folderPicker.launch(null) },
            scale = noScale,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        ) { Text(stringResource(R.string.sources_add_folder), fontSize = 18.sp) }

        if (!hasDrive) {
            Button(
                onClick = onAddDrive,
                scale = noScale,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            ) { Text(stringResource(R.string.sources_add_drive), fontSize = 18.sp) }
        }

        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDone,
                scale = noScale,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            ) { Text(stringResource(R.string.sources_start), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
