package com.sethv.fintrack.service.categorizer

import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.ParsedTransaction

interface TransactionCategorizer {
    fun categorize(transaction: ParsedTransaction): ExpenseCategory
}
