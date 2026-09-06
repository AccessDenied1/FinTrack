package com.sethv.fintrack.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "card_bills",
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["dueDate"]),
        Index(value = ["isPaid"]),
    ],
)
data class CardBillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardId: Long,
    val totalDue: Double,
    val minDue: Double,
    val dueDate: Long,
    val statementLabel: String,
    val generatedAt: Long,
    val isPaid: Boolean,
    val paidAt: Long = 0,
    val paidAmount: Double = 0.0,
    val creditLimit: Double? = null,
    val statementStart: Long = 0L,
)
