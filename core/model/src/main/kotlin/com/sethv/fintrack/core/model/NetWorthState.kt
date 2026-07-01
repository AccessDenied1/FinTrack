package com.sethv.fintrack.core.model

data class NetWorthState(
    val initialBalance: Double,
    val currentBalance: Double,
    val totalCredits: Double,
    val totalDebits: Double,
)
