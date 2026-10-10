package com.photoframe.tv.ui.auth

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframe.tv.R
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/** Derive a short display name from a SAF tree URI (e.g. "primary:DCIM/Frame" -> "Frame"). */
private fun folderNameFromTree(uri: Uri): String {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return ""
    return docId.substringAfterLast('/').substringAfterLast(':').ifBlank { docId }
}

/**
 * First-run setup: connect Google Drive (device authorization grant — code shown on the TV, approve
 * on a phone) or pick a local / USB folder via the Storage Access Framework.
 */
@Composable
fun ConnectScreen(
    onConnectDrive: () -> Unit,
    onLocalFolderPicked: (treeUri: String, name: String) -> Unit,
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onLocalFolderPicked(uri.toString(), folderNameFromTree(uri))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F14)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.widthIn(max = 760.dp),
        ) {
            Text(stringResource(R.string.app_name), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.connect_subtitle),
                color = Color(0xFFB0B8C4),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onConnectDrive) {
                Text(stringResource(R.string.connect_button), fontSize = 20.sp)
            }
            Button(onClick = { folderPicker.launch(null) }) {
                Text(stringResource(R.string.connect_local_button), fontSize = 20.sp)
            }
        }
    }
}
