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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Surface
import com.photoframe.tv.R
import com.photoframe.tv.data.PhotoSource
import com.photoframe.tv.di.AppGraph
import com.photoframe.tv.ui.auth.DeviceConnectScreen
import com.photoframe.tv.ui.settings.SettingsScreen
import com.photoframe.tv.ui.slideshow.SlideshowScreen
import com.photoframe.tv.ui.sources.SourcesScreen
import kotlinx.coroutines.launch

private enum class Screen { Sources, DeviceConnect, Slideshow, Settings }

@Composable
fun PhotoFrameApp(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val driveName = stringResource(R.string.type_drive)

    var screen by remember { mutableStateOf<Screen?>(null) }
    var migrated by remember { mutableStateOf(false) }
    // Whether the Sources screen was opened from Settings (so "done"/back returns there).
    var sourcesFromSettings by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        val s = settings ?: return@LaunchedEffect

        // One-time migration: fold a legacy single-source install into the new sources list.
        if (!migrated) {
            migrated = true
            if (!graph.isDemoMode && s.sources.isEmpty()) {
                s.localTreeUri?.let { graph.settingsRepository.addLocalSource(it, s.localFolderName.ifBlank { driveName }) }
                if (graph.deviceAuthManager.hasRefreshToken) graph.settingsRepository.addDriveSource(driveName)
            }
        }

        // Pick the first screen once (source-aware, including legacy state).
        if (screen == null) {
            val hasAny = graph.isDemoMode || s.sources.isNotEmpty() ||
                s.localTreeUri != null || graph.deviceAuthManager.hasRefreshToken
            screen = if (hasAny) Screen.Slideshow else Screen.Sources
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), colors = surfaceBlack()) {
        when (screen) {
            null -> Unit // brief blank while settings load

            Screen.Sources -> SourcesScreen(
                sources = settings?.sources ?: emptyList(),
                onAddLocal = { uri, name -> scope.launch { graph.settingsRepository.addLocalSource(uri, name) } },
                onAddDrive = {
                    if (graph.deviceAuthManager.hasRefreshToken) {
                        scope.launch { graph.settingsRepository.addDriveSource(driveName) }
                    } else {
                        screen = Screen.DeviceConnect
                    }
                },
                onRemove = { id -> scope.launch { graph.settingsRepository.removeSource(id) } },
                onDone = {
                    val target = if (sourcesFromSettings) Screen.Settings else Screen.Slideshow
                    sourcesFromSettings = false
                    screen = target
                },
                onBack = {
                    when {
                        sourcesFromSettings -> { sourcesFromSettings = false; screen = Screen.Settings }
                        (settings?.sources?.isNotEmpty() == true) -> screen = Screen.Slideshow
                        else -> Unit // nothing configured yet — stay on Sources
                    }
                },
            )

            Screen.DeviceConnect -> DeviceConnectScreen(
                deviceAuth = graph.deviceAuthManager,
                onConnected = {
                    scope.launch {
                        graph.settingsRepository.setPhotoSource(PhotoSource.DRIVE)
                        graph.settingsRepository.addDriveSource(driveName)
                    }
                    screen = Screen.Sources
                },
                onBack = { screen = Screen.Sources },
            )

            Screen.Slideshow -> SlideshowScreen(
                driveRepository = graph.driveRepository,
                localRepository = graph.localRepository,
                settingsRepository = graph.settingsRepository,
                imageLoader = graph.imageLoader,
                isDemoMode = graph.isDemoMode,
                driveConnected = { graph.deviceAuthManager.hasRefreshToken },
                onOpenSettings = { screen = Screen.Settings },
            )

            Screen.Settings -> SettingsScreen(
                settingsRepository = graph.settingsRepository,
                deviceAuth = graph.deviceAuthManager,
                isDemoMode = graph.isDemoMode,
                onManageSources = { sourcesFromSettings = true; screen = Screen.Sources },
                onClose = { screen = Screen.Slideshow },
            )
        }
    }
}

@Composable
private fun surfaceBlack() = androidx.tv.material3.SurfaceDefaults.colors(
    containerColor = Color.Black,
    contentColor = Color.White,
)
