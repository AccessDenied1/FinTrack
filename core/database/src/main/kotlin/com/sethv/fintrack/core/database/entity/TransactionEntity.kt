package com.sethv.fintrack.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val category: String,
    val type: String,
    val dateTime: Long,
    val bank: String,
    val notes: String,
    val smsBody: String,
    val createdAt: Long,
)
