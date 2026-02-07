package com.voiceping.android.data.network.dto

import com.google.gson.annotations.SerializedName

data class EventResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String?,

    @SerializedName("createdAt")
    val createdAt: String?
)
