package com.voiceping.android.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.voiceping.android.data.database.VoicePingDatabase
import com.voiceping.android.data.network.dto.SignalingType
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
            .registerTypeAdapter(SignalingType::class.java, SignalingTypeAdapter())
            .create()
    }

    /**
     * Custom TypeAdapter for SignalingType enum.
     * Maps enum values to kebab-case wire format without relying on @SerializedName
     * annotations, which R8 strips in release builds.
     */
    private class SignalingTypeAdapter : TypeAdapter<SignalingType>() {
        private val toWire = mapOf(
            SignalingType.JOIN_CHANNEL to "join-channel",
            SignalingType.LEAVE_CHANNEL to "leave-channel",
            SignalingType.GET_ROUTER_CAPABILITIES to "get-router-capabilities",
            SignalingType.CREATE_TRANSPORT to "create-transport",
            SignalingType.CONNECT_TRANSPORT to "connect-transport",
            SignalingType.PRODUCE to "produce",
            SignalingType.CONSUME to "consume",
            SignalingType.PTT_START to "ptt-start",
            SignalingType.PTT_STOP to "ptt-stop",
            SignalingType.PTT_DENIED to "ptt-denied",
            SignalingType.SPEAKER_CHANGED to "speaker-changed",
            SignalingType.CHANNEL_STATE to "channel-state",
            SignalingType.ERROR to "error",
            SignalingType.PING to "ping",
            SignalingType.PONG to "pong",
            SignalingType.PERMISSION_UPDATE to "permission-update",
            SignalingType.CHANNEL_LIST to "channel-list",
            SignalingType.FORCE_DISCONNECT to "force-disconnect",
            SignalingType.PRIORITY_PTT_START to "priority-ptt-start",
            SignalingType.PRIORITY_PTT_STOP to "priority-ptt-stop",
            SignalingType.EMERGENCY_BROADCAST_START to "emergency-broadcast-start",
            SignalingType.EMERGENCY_BROADCAST_STOP to "emergency-broadcast-stop",
            SignalingType.PTT_INTERRUPTED to "ptt-interrupted",
            SignalingType.ROLE_INFO to "role-info",
            SignalingType.BAN_USER to "ban-user",
            SignalingType.UNBAN_USER to "unban-user",
        )
        private val fromWire = toWire.entries.associate { it.value to it.key }

        override fun write(out: JsonWriter, value: SignalingType?) {
            out.value(toWire[value])
        }

        override fun read(reader: JsonReader): SignalingType? {
            val value = reader.nextString()
            return fromWire[value]
        }
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
