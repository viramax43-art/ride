package com.rideminiapp.di

import com.rideminiapp.data.local.AppPrefs
import com.rideminiapp.data.local.MmkvStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassengerBearerInterceptor @Inject constructor(
    private val store: MmkvStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = store.getString(AppPrefs.PASSENGER_TOKEN, "")
        if (token.isBlank() || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        val authenticated = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authenticated)
    }
}
