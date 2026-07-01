package com.sethv.fintrack.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "balance_settings")
data class BalanceSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val initialBalance: Double = 0.0,
    val setAt: Long = System.currentTimeMillis(),
)
