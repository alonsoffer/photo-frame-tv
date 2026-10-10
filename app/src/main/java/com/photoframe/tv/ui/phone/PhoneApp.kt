package com.photoframe.tv.ui.phone

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.photoframe.tv.di.AppGraph

/**
 * The phone/tablet experience: a touch-first, portrait management app for the Drive "Photo Frame"
 * folder — connect Google Drive, view/add/delete photos, and pair a TV by scanning its QR.
 */
@Composable
fun PhoneApp(graph: AppGraph) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF5AA9E6), background = Color(0xFF0B0F14), surface = Color(0xFF141A22))
    } else {
        lightColorScheme(primary = Color(0xFF2C6FB0))
    }
    MaterialTheme(colorScheme = colors) {
        Surface {
            var connected by remember { mutableStateOf(graph.deviceAuthManager.hasRefreshToken) }
            if (!connected) {
                PhoneConnectScreen(
                    deviceAuth = graph.deviceAuthManager,
                    onConnected = { connected = true },
                )
            } else {
                PhoneGalleryScreen(
                    graph = graph,
                    onDisconnect = {
                        graph.deviceAuthManager.disconnect()
                        connected = false
                    },
                )
            }
        }
    }
}
