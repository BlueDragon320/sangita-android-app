package com.sangita.app.data.remote.api

import com.sangita.app.data.local.preferences.UserPreferences
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val userPreferences: UserPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = userPreferences.getToken()

        val requestBuilder = original.newBuilder()
            .header("User-Agent", "Sangita-Android-Native/1.0.0")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}
