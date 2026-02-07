package com.voiceping.android.data.network.dto

import com.google.gson.annotations.SerializedName

data class ChannelResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("teamId")
    val teamId: String,

    @SerializedName("teamName")
    val teamName: String
)
