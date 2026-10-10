package com.photoframe.tv.auth

import android.content.Context
import com.photoframe.tv.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Google OAuth 2.0 Device Authorization Grant (RFC 8628) for TVs / limited-input devices.
 * The TV shows a short code + URL; the user approves on a phone/computer. No Play Services and no
 * WebView are involved — only plain HTTPS — so it doesn't touch the on-device consent screen.
 * The refresh token is stored locally and used to mint access tokens for the Drive REST API.
 */
class DeviceAuthManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("device_auth", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var accessToken: String? = null
    @Volatile private var expiresAt: Long = 0L

    val hasRefreshToken: Boolean get() = prefs.getString(KEY_REFRESH, null) != null

    /** Step 1: request a device+user code pair. Throws [DeviceAuthException] with Google's error text. */
    fun requestDeviceCode(): DeviceCode {
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.TV_CLIENT_ID)
            .add("scope", SCOPE)
            .build()
        http.newCall(Request.Builder().url(DEVICE_CODE_URL).post(body).build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = runCatching { json.decodeFromString(ErrorResp.serializer(), text) }.getOrNull()
                throw DeviceAuthException(err?.errorDescription ?: err?.error ?: "HTTP ${resp.code}")
            }
            return json.decodeFromString(DeviceCode.serializer(), text)
        }
    }

    /**
     * Step 2: poll the token endpoint until the user approves (or it's denied/expires).
     * Blocking — call off the main thread.
     */
    fun pollForToken(deviceCode: String, intervalSeconds: Int, expiresInSeconds: Int): PollResult {
        var interval = intervalSeconds.coerceAtLeast(1)
        val deadline = System.currentTimeMillis() + expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(interval * 1000L)
            } catch (_: InterruptedException) {
                return PollResult.Error("cancelled")
            }
            val body = FormBody.Builder()
                .add("client_id", BuildConfig.TV_CLIENT_ID)
                .add("client_secret", BuildConfig.TV_CLIENT_SECRET)
                .add("device_code", deviceCode)
                .add("grant_type", DEVICE_GRANT)
                .build()
            val result = runCatching {
                http.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val tok = json.decodeFromString(TokenResp.serializer(), text)
                        tok.refreshToken?.let { prefs.edit().putString(KEY_REFRESH, it).apply() }
                        cache(tok.accessToken, tok.expiresIn)
                        PollResult.Success
                    } else {
                        when (runCatching { json.decodeFromString(ErrorResp.serializer(), text) }.getOrNull()?.error) {
                            "authorization_pending" -> null // keep waiting
                            "slow_down" -> { interval += 5; null }
                            "access_denied" -> PollResult.Denied
                            "expired_token" -> PollResult.Expired
                            else -> PollResult.Error("HTTP ${resp.code}")
                        }
                    }
                }
            }.getOrElse { null } // network hiccup: keep polling
            if (result != null) return result
        }
        return PollResult.Expired
    }

    /** Returns a valid Drive access token, refreshing via the stored refresh token if needed. */
    fun getFreshAccessTokenBlocking(): String? {
        accessToken?.let { if (System.currentTimeMillis() < expiresAt) return it }
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.TV_CLIENT_ID)
            .add("client_secret", BuildConfig.TV_CLIENT_SECRET)
            .add("refresh_token", refresh)
            .add("grant_type", "refresh_token")
            .build()
        return try {
            http.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return null
                val tok = json.decodeFromString(TokenResp.serializer(), text)
                cache(tok.accessToken, tok.expiresIn)
                tok.accessToken
            }
        } catch (t: Throwable) {
            null
        }
    }

    fun disconnect() {
        accessToken = null
        expiresAt = 0L
        prefs.edit().remove(KEY_REFRESH).apply()
    }

    private fun cache(token: String?, expiresIn: Int) {
        accessToken = token
        expiresAt = if (token != null) {
            System.currentTimeMillis() + (expiresIn - 60).coerceAtLeast(60) * 1000L
        } else 0L
    }

    private companion object {
        // Non-sensitive per-file scope: the app sees only files it created (or that were shared to it
        // by another instance of this same client id). Allowed on the device flow; no verification.
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val KEY_REFRESH = "refresh_token"
    }
}

class DeviceAuthException(message: String) : Exception(message)

sealed interface PollResult {
    data object Success : PollResult
    data object Denied : PollResult
    data object Expired : PollResult
    data class Error(val message: String) : PollResult
}

@Serializable
data class DeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String = "https://www.google.com/device",
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_url_complete") val verificationUrlComplete: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 1800,
    val interval: Int = 5,
) {
    /** Human-readable page to visit. */
    val url: String get() = verificationUri ?: verificationUrl

    /** URL to encode in the QR — pre-fills the code so scanning goes straight to approval. */
    val qrContent: String
        get() = verificationUrlComplete ?: verificationUriComplete ?: "$url?user_code=$userCode"
}

@Serializable
private data class TokenResp(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 3600,
)

@Serializable
private data class ErrorResp(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
