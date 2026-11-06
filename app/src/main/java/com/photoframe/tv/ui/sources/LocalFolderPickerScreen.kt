package com.photoframe.tv.ui.sources

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.photoframe.tv.R
import com.photoframe.tv.drive.LocalBucket
import com.photoframe.tv.drive.LocalPhotoRepository
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

private sealed interface PickerState {
    data object Loading : PickerState
    data object Denied : PickerState
    data class Loaded(val buckets: List<LocalBucket>) : PickerState
}

/** The runtime permission needed to read local photos, by API level. */
private fun mediaPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Built-in folder picker for local/USB photos — lists MediaStore image folders (buckets) so it works
 * on Android TVs that have no system file picker. Requests the media-read permission first.
 */
@Composable
fun LocalFolderPickerScreen(
    repo: LocalPhotoRepository,
    onPick: (uri: String, name: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PickerState>(PickerState.Loading) }
    var reload by remember { mutableStateOf(0) }

    val permission = mediaPermission()
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok -> if (ok) reload++ else state = PickerState.Denied }

    LaunchedEffect(Unit) { if (!granted) requestPermission.launch(permission) }

    LaunchedEffect(reload) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            state = PickerState.Loading
            state = PickerState.Loaded(runCatching { repo.listBuckets() }.getOrDefault(emptyList()))
        }
    }

    val noScale = ButtonDefaults.scale(focusedScale = 1f)

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
            stringResource(R.string.picker_title),
            color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))

        when (val s = state) {
            PickerState.Loading ->
                Text(stringResource(R.string.picker_loading), color = Color(0xFFB0B8C4), fontSize = 16.sp)

            PickerState.Denied -> {
                Text(
                    stringResource(R.string.picker_denied),
                    color = Color(0xFFB0B8C4), fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
                Button(onClick = { requestPermission.launch(permission) }, scale = noScale) {
                    Text(stringResource(R.string.picker_grant), fontSize = 18.sp)
                }
            }

            is PickerState.Loaded -> {
                if (s.buckets.isEmpty()) {
                    Text(
                        stringResource(R.string.picker_empty),
                        color = Color(0xFF8A94A6), fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                } else {
                    s.buckets.forEach { b ->
                        Button(
                            onClick = { onPick("bucket://${b.id}", b.name) },
                            scale = noScale,
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        ) { Text("${b.name}   ·   ${b.count}", fontSize = 18.sp) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack, scale = noScale) { Text(stringResource(R.string.action_back), fontSize = 16.sp) }
    }
}
