package com.sethv.fintrack.service.categorizer

import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.ParsedTransaction
import javax.inject.Inject

class KeywordBasedCategorizer @Inject constructor() : TransactionCategorizer {

    private data class Rule(
        val category: ExpenseCategory,
        val keywords: Set<String>,
        // false = match the merchant field only (brand tokens too generic or too
        // collision-prone to trust inside free-form SMS boilerplate).
        val matchInSmsBody: Boolean = true,
    )

    private val rules: List<Rule> = listOf(
        Rule(
            ExpenseCategory.FOOD,
            setOf("swiggy", "zomato", "dominos", "mcdonald", "pizza", "restaurant", "cafe", "food"),
        ),
        Rule(
            ExpenseCategory.GROCERIES,
            setOf("bigbasket", "grofers", "blinkit", "grocery", "dmart", "instamart", "zepto"),
        ),
        Rule(
            ExpenseCategory.GROCERIES,
            setOf("more"),
            matchInSmsBody = false,
        ),
        Rule(
            ExpenseCategory.SHOPPING,
            setOf("amazon", "flipkart", "myntra", "ajio", "shopping"),
        ),
        Rule(
            ExpenseCategory.FUEL,
            setOf("petrol", "diesel", "hp", "iocl", "bpcl", "fuel", "fuels"),
        ),
        Rule(
            ExpenseCategory.TRANSPORT,
            setOf("uber", "ola", "rapido", "metro", "bus", "train"),
        ),
        Rule(
            ExpenseCategory.BILLS,
            setOf("electricity", "water", "gas", "broadband", "wifi", "jio", "airtel"),
        ),
        Rule(
            ExpenseCategory.ENTERTAINMENT,
            setOf("netflix", "spotify", "hotstar", "prime", "movie", "pvr", "inox"),
        ),
        Rule(
            ExpenseCategory.HEALTHCARE,
            setOf("apollo", "pharmacy", "hospital", "doctor", "medplus", "1mg"),
        ),
        Rule(
            ExpenseCategory.TRAVEL,
            setOf("irctc", "makemytrip", "goibibo", "flight", "hotel", "booking"),
        ),
        Rule(
            ExpenseCategory.RENT,
            setOf("rent", "landlord", "housing"),
        ),
        Rule(
            ExpenseCategory.SUBSCRIPTION,
            setOf("subscription", "membership"),
        ),
    )

    // Precompiled word-boundary patterns — substring matching caused mass
    // mis-categorization ("more" ⊂ "for more details" → GROCERIES,
    // "rent" ⊂ "current balance" → RENT, "bus" ⊂ "business" → TRANSPORT).
    private val compiledRules: List<Triple<ExpenseCategory, List<Regex>, Boolean>> by lazy {
        rules.map { rule ->
            Triple(rule.category, rule.keywords.map { compileWordPattern(it) }, rule.matchInSmsBody)
        }
    }

    override fun categorize(transaction: ParsedTransaction): ExpenseCategory {
        val merchant = transaction.merchant.lowercase()

        for ((category, patterns, _) in compiledRules) {
            if (patterns.any { it.containsMatchIn(merchant) }) return category
        }

        val smsBody = transaction.smsBody.lowercase()
        for ((category, patterns, matchInSmsBody) in compiledRules) {
            if (matchInSmsBody && patterns.any { it.containsMatchIn(smsBody) }) return category
        }

        return ExpenseCategory.OTHERS
    }

    private fun compileWordPattern(keyword: String): Regex =
        Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE)
}
