package com.sethv.fintrack.core.model

data class CreditCard(
    val id: Long = 0,
    val bankName: String,
    val lastFour: String,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** e.g. "HDFC •• 4521" */
    fun displayName(): String = "${bankName.uppercase()} •• $lastFour"
}
