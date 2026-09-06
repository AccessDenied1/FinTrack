package com.sethv.fintrack.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credit_cards",
    indices = [
        Index(value = ["bankName", "lastFour"], unique = true),
    ],
)
data class BankCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bankName: String,
    val lastFour: String,
    val label: String,
    val createdAt: Long,
    val creditLimitOverride: Double? = null,
)