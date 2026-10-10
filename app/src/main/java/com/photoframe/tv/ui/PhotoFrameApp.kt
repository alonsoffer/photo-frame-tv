package com.photoframe.tv.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Surface
import com.photoframe.tv.data.PhotoSource
import com.photoframe.tv.data.SlideshowSettings
import com.photoframe.tv.di.AppGraph
import com.photoframe.tv.ui.auth.ConnectScreen
import com.photoframe.tv.ui.auth.DeviceConnectScreen
import com.photoframe.tv.ui.settings.FolderBrowserScreen
import com.photoframe.tv.ui.settings.SettingsScreen
import com.photoframe.tv.ui.slideshow.SlideshowScreen
import kotlinx.coroutines.launch

private enum class Screen { Connect, DeviceConnect, Slideshow, Settings, Folders }

@Composable
fun PhotoFrameApp(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

    // Decide the first screen once settings have loaded (source-aware).
    var screen by remember { mutableStateOf<Screen?>(null) }
    LaunchedEffect(settings) {
        if (screen == null) {
            val s = settings ?: return@LaunchedEffect
            screen = if (needsSetup(s, graph)) Screen.Connect else Screen.Slideshow
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), colors = surfaceBlack()) {
        when (screen) {
            null -> Unit // brief blank while settings load

            Screen.Connect -> ConnectScreen(
                onConnectDrive = { screen = Screen.DeviceConnect },
                onLocalFolderPicked = { uri, name ->
                    scope.launch { graph.settingsRepository.setLocalFolder(uri, name) }
                    screen = Screen.Slideshow
                },
            )

            Screen.DeviceConnect -> DeviceConnectScreen(
                deviceAuth = graph.deviceAuthManager,
                onConnected = {
                    scope.launch { graph.settingsRepository.setPhotoSource(PhotoSource.DRIVE) }
                    screen = Screen.Slideshow
                },
                onBack = { screen = Screen.Connect },
            )

            Screen.Slideshow -> SlideshowScreen(
                driveRepository = graph.driveRepository,
                localRepository = graph.localRepository,
                settingsRepository = graph.settingsRepository,
                imageLoader = graph.imageLoader,
                onOpenSettings = { screen = Screen.Settings },
            )

            Screen.Settings -> SettingsScreen(
                settingsRepository = graph.settingsRepository,
                deviceAuth = graph.deviceAuthManager,
                isDemoMode = graph.isDemoMode,
                onOpenFolderBrowser = { screen = Screen.Folders },
                onConnectDrive = { screen = Screen.DeviceConnect },
                onDisconnect = {
                    graph.deviceAuthManager.disconnect()
                    screen = Screen.Connect
                },
                onClose = { screen = Screen.Slideshow },
            )

            Screen.Folders -> FolderBrowserScreen(
                driveRepository = graph.driveRepository,
                onPick = { id, name ->
                    scope.launch { graph.settingsRepository.setFolder(id, name) }
                    screen = Screen.Settings
                },
                onClose = { screen = Screen.Settings },
            )
        }
    }
}

/** Whether first-run setup is still needed for the currently-selected photo source. */
private fun needsSetup(s: SlideshowSettings, graph: AppGraph): Boolean {
    if (graph.isDemoMode) return false
    return when (s.photoSource) {
        PhotoSource.LOCAL -> s.localTreeUri == null
        PhotoSource.DRIVE -> !graph.deviceAuthManager.hasRefreshToken
    }
}

@Composable
private fun surfaceBlack() = androidx.tv.material3.SurfaceDefaults.colors(
    containerColor = Color.Black,
    contentColor = Color.White,
)
