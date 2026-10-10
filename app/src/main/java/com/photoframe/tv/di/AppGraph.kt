package com.photoframe.tv.di

import android.content.Context
import coil.ImageLoader
import com.photoframe.tv.BuildConfig
import com.photoframe.tv.auth.AuthInterceptor
import com.photoframe.tv.auth.DeviceAuthManager
import com.photoframe.tv.data.SettingsRepository
import com.photoframe.tv.drive.DriveRepository
import com.photoframe.tv.drive.FakeDriveRepository
import com.photoframe.tv.drive.GoogleDriveRepository
import com.photoframe.tv.drive.LocalPhotoRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Lightweight manual dependency graph (a service locator). Built once in [com.photoframe.tv.App]
 * and shared across the app.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    /** Google Drive auth via the TV/limited-input device authorization grant (RFC 8628). */
    val deviceAuthManager = DeviceAuthManager(appContext)

    /** True when there's no real Drive access, so built-in demo photos are shown. */
    val isDemoMode: Boolean = BuildConfig.DEMO_MODE

    val settingsRepository = SettingsRepository(appContext)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor { deviceAuthManager.getFreshAccessTokenBlocking() })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    val driveRepository: DriveRepository =
        if (isDemoMode) FakeDriveRepository() else GoogleDriveRepository(okHttpClient, json)

    /** Reads photos from an on-device / USB folder picked via the Storage Access Framework. */
    val localRepository: DriveRepository = LocalPhotoRepository(appContext)

    /** Coil loader that reuses our authenticated OkHttp client so Drive media URLs load with the Bearer token. */
    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .okHttpClient(okHttpClient)
        .crossfade(false) // transitions are handled by the slideshow UI
        .respectCacheHeaders(false)
        .build()

    /** Whether the user still needs to connect Google Drive before real photos load. */
    fun needsConnect(): Boolean = !isDemoMode && !deviceAuthManager.hasRefreshToken
}
