package com.sethv.fintrack.core.database.converter

import androidx.room.TypeConverter
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.TransactionType

class Converters {

    @TypeConverter
    fun fromExpenseCategory(category: ExpenseCategory): String = category.name

    @TypeConverter
    fun toExpenseCategory(value: String): ExpenseCategory = ExpenseCategory.valueOf(value)

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
}
