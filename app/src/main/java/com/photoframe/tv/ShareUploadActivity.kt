package com.photoframe.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import com.photoframe.tv.ui.theme.PhotoFrameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives images shared from the phone's gallery (Android Share sheet) and uploads them into the
 * app's "Photo Frame" folder in Google Drive, so the TV (same OAuth client) can display them.
 */
class ShareUploadActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as App).graph
        val uris = extractUris(intent)

        setContent {
            PhotoFrameTheme {
                var message by remember { mutableStateOf(uploadingText(uris.size, 0)) }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F14)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(message, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(40.dp))
                }

                LaunchedEffectOnce {
                    if (!graph.deviceAuthManager.hasRefreshToken) {
                        message = "Open Photo Frame and connect Google Drive first, then share again."
                        delay(3500); finish(); return@LaunchedEffectOnce
                    }
                    if (uris.isEmpty()) { finish(); return@LaunchedEffectOnce }
                    var done = 0
                    var failed = 0
                    for (uri in uris) {
                        val ok = runCatching {
                            val (bytes, mime, name) = withContext(Dispatchers.IO) { read(uri) }
                            graph.driveRepository.uploadPhoto(name, mime, bytes)
                        }.isSuccess
                        if (ok) done++ else failed++
                        message = uploadingText(uris.size, done)
                    }
                    message = if (failed == 0) "Uploaded $done photo(s) to your frame ✓"
                              else "Uploaded $done, failed $failed"
                    delay(1800); finish()
                }
            }
        }
    }

    private fun uploadingText(total: Int, done: Int) = "Uploading to your frame…  $done / $total"

    private fun extractUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        Intent.ACTION_SEND_MULTIPLE ->
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }

    private fun read(uri: Uri): Triple<ByteArray, String, String> {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val name = queryName(uri) ?: "photo_${System.currentTimeMillis()}.jpg"
        return Triple(bytes, mime, name)
    }

    private fun queryName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    /** Run a coroutine once when the composition first appears (tiny helper to keep onCreate tidy). */
    @Composable
    private fun LaunchedEffectOnce(block: suspend () -> Unit) {
        androidx.compose.runtime.LaunchedEffect(Unit) { lifecycleScope.launch { block() } }
    }
}
