package com.wifikidscontrol.di

import com.wifikidscontrol.network.LocalVpnService
import com.wifikidscontrol.network.RouterApi
import com.wifikidscontrol.network.RouterApiFactory
import com.wifikidscontrol.network.RouterConfig
import com.wifikidscontrol.network.RouterType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    fun provideRouterApiFactory(): RouterApiFactory.type = RouterApiFactory
}