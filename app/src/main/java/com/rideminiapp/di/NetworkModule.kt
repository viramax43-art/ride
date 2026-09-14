package com.rideminiapp.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.rideminiapp.BuildConfig
import com.rideminiapp.data.local.AppPrefs
import com.rideminiapp.data.local.CookieStore
import com.rideminiapp.data.local.MmkvStore
import com.rideminiapp.data.remote.NominatimClient
import com.rideminiapp.data.remote.OsrmClient
import com.rideminiapp.data.remote.RideApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
annotation class PassengerApi

@Qualifier
annotation class CookieApi

@Qualifier
annotation class PublicApi

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideMmkvStore(): MmkvStore = MmkvStore()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideCookieStore(store: MmkvStore): CookieStore = CookieStore(store)

    @Provides
    @Singleton
    fun provideNominatimClient(): NominatimClient = NominatimClient()

    @Provides
    @Singleton
    fun provideOsrmClient(): OsrmClient = OsrmClient()

    @Provides
    @Singleton
    fun provideBaseUrlInterceptor(store: MmkvStore): BackendBaseUrlInterceptor =
        BackendBaseUrlInterceptor(store, BuildConfig.DEFAULT_API_BASE_URL)

    @Provides
    @Singleton
    @PublicApi
    fun providePublicOkHttpClient(): OkHttpClient = baseClientBuilder().build()

    @Provides
    @Singleton
    @PassengerApi
    fun providePassengerOkHttpClient(): OkHttpClient = baseClientBuilder().build()

    @Provides
    @Singleton
    @CookieApi
    fun provideCookieOkHttpClient(
        cookieStore: CookieStore,
        bearerInterceptor: PassengerBearerInterceptor,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(bearerInterceptor)
        .cookieJar(cookieStore)
        .build()

    @Provides
    @Singleton
    fun provideRetrofitBuilder(gson: Gson): Retrofit.Builder = Retrofit.Builder()
        .baseUrl("https://placeholder.invalid/")
        .addConverterFactory(GsonConverterFactory.create(gson))

    @Provides
    @Singleton
    @PublicApi
    fun providePublicApi(
        retrofitBuilder: Retrofit.Builder,
        @PublicApi okHttpClient: OkHttpClient,
        baseUrlInterceptor: BackendBaseUrlInterceptor,
    ): RideApiService = retrofitBuilder
        .client(okHttpClient.newBuilder().addInterceptor(baseUrlInterceptor).build())
        .build()
        .create(RideApiService::class.java)

    @Provides
    @Singleton
    @PassengerApi
    fun providePassengerApi(
        retrofitBuilder: Retrofit.Builder,
        @PassengerApi okHttpClient: OkHttpClient,
        bearerInterceptor: PassengerBearerInterceptor,
        baseUrlInterceptor: BackendBaseUrlInterceptor,
    ): RideApiService = retrofitBuilder
        .client(okHttpClient.newBuilder().addInterceptor(baseUrlInterceptor).addInterceptor(bearerInterceptor).build())
        .build()
        .create(RideApiService::class.java)

    @Provides
    @Singleton
    @CookieApi
    fun provideCookieApi(
        retrofitBuilder: Retrofit.Builder,
        @CookieApi okHttpClient: OkHttpClient,
        baseUrlInterceptor: BackendBaseUrlInterceptor,
    ): RideApiService = retrofitBuilder
        .client(okHttpClient.newBuilder().addInterceptor(baseUrlInterceptor).build())
        .build()
        .create(RideApiService::class.java)

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
}
