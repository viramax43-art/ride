package com.rideminiapp.di

import com.rideminiapp.BuildConfig
import com.rideminiapp.data.local.AppPrefs
import com.rideminiapp.data.local.MmkvStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class BackendBaseUrlInterceptor(
    private val store: MmkvStore,
    private val defaultBaseUrl: String = BuildConfig.DEFAULT_API_BASE_URL,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configured = store.getString(AppPrefs.API_BASE_URL, defaultBaseUrl).ifBlank { defaultBaseUrl }
        val base = configured.toHttpUrl()
        val newUrl = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
