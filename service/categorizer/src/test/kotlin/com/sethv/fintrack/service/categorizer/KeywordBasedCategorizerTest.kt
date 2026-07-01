package com.sethv.fintrack.service.categorizer

import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KeywordBasedCategorizerTest {

    private lateinit var categorizer: KeywordBasedCategorizer

    @Before
    fun setup() {
        categorizer = KeywordBasedCategorizer()
    }

    private fun createTransaction(merchant: String, smsBody: String = "") = ParsedTransaction(
        amount = 100.0,
        merchant = merchant,
        type = TransactionType.DEBIT,
        dateTime = System.currentTimeMillis(),
        bank = "TestBank",
        smsBody = smsBody,
    )

    @Test
    fun `categorizes food merchants`() {
        assertEquals(ExpenseCategory.FOOD, categorizer.categorize(createTransaction("SWIGGY")))
        assertEquals(ExpenseCategory.FOOD, categorizer.categorize(createTransaction("Zomato Order")))
        assertEquals(ExpenseCategory.FOOD, categorizer.categorize(createTransaction("DOMINOS PIZZA")))
        assertEquals(ExpenseCategory.FOOD, categorizer.categorize(createTransaction("Cafe Coffee Day")))
    }

    @Test
    fun `categorizes grocery merchants`() {
        assertEquals(ExpenseCategory.GROCERIES, categorizer.categorize(createTransaction("BigBasket")))
        assertEquals(ExpenseCategory.GROCERIES, categorizer.categorize(createTransaction("BLINKIT")))
        assertEquals(ExpenseCategory.GROCERIES, categorizer.categorize(createTransaction("DMart Ready")))
    }

    @Test
    fun `categorizes shopping merchants`() {
        assertEquals(ExpenseCategory.SHOPPING, categorizer.categorize(createTransaction("Amazon")))
        assertEquals(ExpenseCategory.SHOPPING, categorizer.categorize(createTransaction("FLIPKART")))
        assertEquals(ExpenseCategory.SHOPPING, categorizer.categorize(createTransaction("Myntra Fashion")))
    }

    @Test
    fun `categorizes fuel merchants`() {
        assertEquals(ExpenseCategory.FUEL, categorizer.categorize(createTransaction("HP Petrol Pump")))
        assertEquals(ExpenseCategory.FUEL, categorizer.categorize(createTransaction("IOCL Fuel Station")))
        assertEquals(ExpenseCategory.FUEL, categorizer.categorize(createTransaction("BPCL")))
    }

    @Test
    fun `categorizes transport merchants`() {
        assertEquals(ExpenseCategory.TRANSPORT, categorizer.categorize(createTransaction("UBER INDIA")))
        assertEquals(ExpenseCategory.TRANSPORT, categorizer.categorize(createTransaction("Ola Cabs")))
        assertEquals(ExpenseCategory.TRANSPORT, categorizer.categorize(createTransaction("Rapido Bike")))
    }

    @Test
    fun `categorizes bill merchants`() {
        assertEquals(ExpenseCategory.BILLS, categorizer.categorize(createTransaction("Jio Recharge")))
        assertEquals(ExpenseCategory.BILLS, categorizer.categorize(createTransaction("Airtel Postpaid")))
        assertEquals(ExpenseCategory.BILLS, categorizer.categorize(createTransaction("Electricity Board")))
    }

    @Test
    fun `categorizes entertainment merchants`() {
        assertEquals(ExpenseCategory.ENTERTAINMENT, categorizer.categorize(createTransaction("Netflix")))
        assertEquals(ExpenseCategory.ENTERTAINMENT, categorizer.categorize(createTransaction("Spotify Premium")))
        assertEquals(ExpenseCategory.ENTERTAINMENT, categorizer.categorize(createTransaction("PVR Cinemas")))
    }

    @Test
    fun `categorizes healthcare merchants`() {
        assertEquals(ExpenseCategory.HEALTHCARE, categorizer.categorize(createTransaction("Apollo Pharmacy")))
        assertEquals(ExpenseCategory.HEALTHCARE, categorizer.categorize(createTransaction("1mg")))
        assertEquals(ExpenseCategory.HEALTHCARE, categorizer.categorize(createTransaction("MedPlus")))
    }

    @Test
    fun `categorizes travel merchants`() {
        assertEquals(ExpenseCategory.TRAVEL, categorizer.categorize(createTransaction("IRCTC")))
        assertEquals(ExpenseCategory.TRAVEL, categorizer.categorize(createTransaction("MakeMyTrip")))
        assertEquals(ExpenseCategory.TRAVEL, categorizer.categorize(createTransaction("Goibibo Hotels")))
    }

    @Test
    fun `categorizes rent`() {
        assertEquals(ExpenseCategory.RENT, categorizer.categorize(createTransaction("RENT Payment")))
    }

    @Test
    fun `categorizes subscription`() {
        assertEquals(ExpenseCategory.SUBSCRIPTION, categorizer.categorize(createTransaction("Subscription Renewal")))
    }

    @Test
    fun `defaults to OTHERS for unknown merchants`() {
        assertEquals(ExpenseCategory.OTHERS, categorizer.categorize(createTransaction("RANDOM STORE XYZ")))
        assertEquals(ExpenseCategory.OTHERS, categorizer.categorize(createTransaction("Unknown")))
    }

    @Test
    fun `searches SMS body when merchant has no match`() {
        val transaction = createTransaction(
            merchant = "ABCXYZ123",
            smsBody = "Rs.350 debited to ABCXYZ123 via Swiggy UPI",
        )
        assertEquals(ExpenseCategory.FOOD, categorizer.categorize(transaction))
    }
}
