package com.sethv.fintrack.core.model

data class RawSms(
    val sender: String,
    val body: String,
    val timestamp: Long,
)
