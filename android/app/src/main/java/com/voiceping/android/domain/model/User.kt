package com.voiceping.android.domain.model

data class User(
    val id: String,
    val name: String,
    val email: String = ""
)
