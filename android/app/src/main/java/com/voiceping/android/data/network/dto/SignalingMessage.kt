package com.voiceping.android.data.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Signaling message DTO matching src/shared/protocol.ts
 *
 * Server protocol uses kebab-case enum values, serialized via @SerializedName annotations.
 */
data class SignalingMessage(
    val type: SignalingType,
    val id: String? = null,
    val data: Map<String, Any>? = null,
    val error: String? = null
)

/**
 * Signaling message types matching SignalingType enum from src/shared/protocol.ts
 *
 * IMPORTANT: @SerializedName values MUST match server's kebab-case exactly.
 */
enum class SignalingType {
    @SerializedName("join-channel")
    JOIN_CHANNEL,

    @SerializedName("leave-channel")
    LEAVE_CHANNEL,

    @SerializedName("get-router-capabilities")
    GET_ROUTER_CAPABILITIES,

    @SerializedName("create-transport")
    CREATE_TRANSPORT,

    @SerializedName("connect-transport")
    CONNECT_TRANSPORT,

    @SerializedName("produce")
    PRODUCE,

    @SerializedName("consume")
    CONSUME,

    @SerializedName("ptt-start")
    PTT_START,

    @SerializedName("ptt-stop")
    PTT_STOP,

    @SerializedName("ptt-denied")
    PTT_DENIED,

    @SerializedName("speaker-changed")
    SPEAKER_CHANGED,

    @SerializedName("channel-state")
    CHANNEL_STATE,

    @SerializedName("error")
    ERROR,

    @SerializedName("ping")
    PING,

    @SerializedName("pong")
    PONG,

    @SerializedName("permission-update")
    PERMISSION_UPDATE,

    @SerializedName("channel-list")
    CHANNEL_LIST,

    @SerializedName("force-disconnect")
    FORCE_DISCONNECT,

    @SerializedName("priority-ptt-start")
    PRIORITY_PTT_START,

    @SerializedName("priority-ptt-stop")
    PRIORITY_PTT_STOP,

    @SerializedName("emergency-broadcast-start")
    EMERGENCY_BROADCAST_START,

    @SerializedName("emergency-broadcast-stop")
    EMERGENCY_BROADCAST_STOP,

    @SerializedName("ptt-interrupted")
    PTT_INTERRUPTED,

    @SerializedName("role-info")
    ROLE_INFO,

    @SerializedName("ban-user")
    BAN_USER,

    @SerializedName("unban-user")
    UNBAN_USER
}
