package com.photoframe.tv.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds a Bearer token to Google API requests and retries once on 401 after forcing a refresh.
 * The token comes from [accessToken] (the device-flow refresh-token grant). Requests to non-Google
 * hosts (e.g. demo sample images, Drive thumbnail CDN) are passed through untouched.
 */
class AuthInterceptor(private val accessToken: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!original.url.host.endsWith("googleapis.com")) {
            return chain.proceed(original)
        }

        val token = accessToken() ?: return chain.proceed(original) // not connected yet; let it fail normally

        var response = chain.proceed(original.withBearer(token))
        if (response.code == 401) {
            response.close()
            val refreshed = accessToken()
            if (refreshed != null) {
                response = chain.proceed(original.withBearer(refreshed))
            }
        }
        return response
    }

    private fun okhttp3.Request.withBearer(token: String) =
        newBuilder().header("Authorization", "Bearer $token").build()
}
