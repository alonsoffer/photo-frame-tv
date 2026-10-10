package com.photoframe.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframe.tv.R
import com.photoframe.tv.drive.DriveFolder
import com.photoframe.tv.drive.DriveRepository
import androidx.tv.material3.Button
import androidx.tv.material3.Text

private data class Crumb(val id: String?, val name: String)

/**
 * Lets the user walk their Drive folder tree and pick one as the photo source.
 * "Use this folder" selects the folder currently being viewed.
 */
@Composable
fun FolderBrowserScreen(
    driveRepository: DriveRepository,
    onPick: (id: String?, name: String) -> Unit,
    onClose: () -> Unit,
) {
    // Navigation stack; starts at My Drive root.
    val myDrive = stringResource(R.string.my_drive)
    var stack by remember { mutableStateOf(listOf(Crumb(null, myDrive))) }
    var children by remember { mutableStateOf<List<DriveFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val current = stack.last()

    LaunchedEffect(current.id) {
        loading = true
        error = null
        try {
            children = driveRepository.listFolders(current.id)
        } catch (t: Throwable) {
            error = t.message ?: "Could not load folders"
            children = emptyList()
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == Key.Back) {
                    if (stack.size > 1) { stack = stack.dropLast(1); true } else { onClose(); true }
                } else false
            }
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.folder_title), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(stack.joinToString(" › ") { it.name }, color = Color(0xFF8A94A6), fontSize = 15.sp)

        Button(
            onClick = { onPick(current.id, current.name) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("✓  " + stringResource(R.string.folder_use_this, current.name), fontWeight = FontWeight.Bold) }

        when {
            loading -> Text(stringResource(R.string.folder_loading), color = Color(0xFFB0B8C4), fontSize = 16.sp)
            error != null -> Text(error!!, color = Color(0xFFE06C75), fontSize = 16.sp)
            children.isEmpty() -> Text(stringResource(R.string.folder_none), color = Color(0xFF8A94A6), fontSize = 16.sp)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(children, key = { it.id }) { folder ->
                    Button(
                        onClick = { stack = stack + Crumb(folder.id, folder.name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("📁  ${folder.name}") }
                }
            }
        }
    }
}
