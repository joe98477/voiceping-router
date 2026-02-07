package com.voiceping.android.di

import com.google.gson.Gson
import com.voiceping.android.BuildConfig
import com.voiceping.android.data.api.AuthApi
import com.voiceping.android.data.repository.AuthRepository
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.data.storage.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTokenManager(tokenManager: TokenManager): TokenManager {
        return tokenManager
    }

    @Provides
    @Singleton
    fun providePreferencesManager(preferencesManager: PreferencesManager): PreferencesManager {
        return preferencesManager
    }

    @Provides
    @Singleton
    fun provideAuthRepository(authRepository: AuthRepository): AuthRepository {
        return authRepository
    }
}
