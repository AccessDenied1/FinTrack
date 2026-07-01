package com.sethv.fintrack.service.categorizer

import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.ParsedTransaction
import javax.inject.Inject

class KeywordBasedCategorizer @Inject constructor() : TransactionCategorizer {

    private val keywordCategoryMap: Map<ExpenseCategory, Set<String>> = mapOf(
        ExpenseCategory.FOOD to setOf(
            "swiggy", "zomato", "dominos", "mcdonald", "pizza", "restaurant", "cafe", "food",
        ),
        ExpenseCategory.GROCERIES to setOf(
            "bigbasket", "grofers", "blinkit", "grocery", "dmart", "more",
        ),
        ExpenseCategory.SHOPPING to setOf(
            "amazon", "flipkart", "myntra", "ajio", "shopping",
        ),
        ExpenseCategory.FUEL to setOf(
            "petrol", "diesel", "hp", "iocl", "bpcl", "fuel",
        ),
        ExpenseCategory.TRANSPORT to setOf(
            "uber", "ola", "rapido", "metro", "bus", "train",
        ),
        ExpenseCategory.BILLS to setOf(
            "electricity", "water", "gas", "broadband", "wifi", "jio", "airtel",
        ),
        ExpenseCategory.ENTERTAINMENT to setOf(
            "netflix", "spotify", "hotstar", "prime", "movie", "pvr", "inox",
        ),
        ExpenseCategory.HEALTHCARE to setOf(
            "apollo", "pharmacy", "hospital", "doctor", "medplus", "1mg",
        ),
        ExpenseCategory.TRAVEL to setOf(
            "irctc", "makemytrip", "goibibo", "flight", "hotel", "booking",
        ),
        ExpenseCategory.RENT to setOf(
            "rent", "landlord", "housing",
        ),
        ExpenseCategory.SUBSCRIPTION to setOf(
            "subscription", "membership",
        ),
    )

    override fun categorize(transaction: ParsedTransaction): ExpenseCategory {
        val merchantLower = transaction.merchant.lowercase()
        val smsLower = transaction.smsBody.lowercase()
        val searchText = "$merchantLower $smsLower"

        for ((category, keywords) in keywordCategoryMap) {
            if (keywords.any { keyword -> searchText.contains(keyword) }) {
                return category
            }
        }
        return ExpenseCategory.OTHERS
    }
}
