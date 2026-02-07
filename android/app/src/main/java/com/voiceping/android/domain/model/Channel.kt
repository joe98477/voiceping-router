package com.voiceping.android.domain.model

data class Channel(
    val id: String,
    val name: String,
    val teamId: String,
    val teamName: String,
    val currentSpeaker: User? = null,
    val userCount: Int = 0
)
