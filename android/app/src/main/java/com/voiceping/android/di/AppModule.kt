package com.voiceping.android.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.voiceping.android.data.database.VoicePingDatabase
import com.voiceping.android.data.database.dao.ChannelDao
import com.voiceping.android.data.database.dao.EventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * In-memory cookie jar that persists session cookies for the app lifetime.
 * Control-plane uses express-session cookies for authentication.
 */
class SessionCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return store[url.host] ?: emptyList()
    }

    fun clear() {
        store.clear()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideSessionCookieJar(): SessionCookieJar {
        return SessionCookieJar()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: SessionCookieJar): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoicePingDatabase {
        return Room.databaseBuilder(
            context,
            VoicePingDatabase::class.java,
            "voiceping_db"
        ).fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideEventDao(database: VoicePingDatabase): EventDao {
        return database.eventDao()
    }

    @Provides
    fun provideChannelDao(database: VoicePingDatabase): ChannelDao {
        return database.channelDao()
    }
}
