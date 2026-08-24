package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserUtilsTest {

    @Test
    fun `parseAmount extracts Rs format`() {
        assertEquals(350.0, ParserUtils.parseAmount("Rs.350.00 debited")!!, 0.01)
    }

    @Test
    fun `parseAmount extracts Rs with space`() {
        assertEquals(1500.0, ParserUtils.parseAmount("Rs 1,500.00 debited")!!, 0.01)
    }

    @Test
    fun `parseAmount extracts INR format`() {
        assertEquals(2500.50, ParserUtils.parseAmount("INR 2,500.50 credited")!!, 0.01)
    }

    @Test
    fun `parseAmount handles amount without decimals`() {
        assertEquals(1000.0, ParserUtils.parseAmount("Rs.1,000 debited")!!, 0.01)
    }

    @Test
    fun `parseAmount returns null for no amount`() {
        assertNull(ParserUtils.parseAmount("No amount here"))
    }

    @Test
    fun `parseAmount extracts rupee symbol`() {
        assertEquals(450.0, ParserUtils.parseAmount("₹450.00 debited from a/c")!!, 0.01)
        assertEquals(75.5, ParserUtils.parseAmount("₹75.50 paid to merchant")!!, 0.01)
        assertEquals(1200.0, ParserUtils.parseAmount("Amount: ₹1,200.00 credited")!!, 0.01)
    }

    @Test
    fun `detectTransactionType identifies debit`() {
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Rs.500 debited from account"))
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Paid Rs 200 to merchant"))
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Rs 100 spent at store"))
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Rs.300 sent to friend"))
    }

    @Test
    fun `detectTransactionType identifies credit`() {
        assertEquals(TransactionType.CREDIT, ParserUtils.detectTransactionType("Rs.5000 credited to account"))
        assertEquals(TransactionType.CREDIT, ParserUtils.detectTransactionType("Rs 1000 received from person"))
        assertEquals(TransactionType.CREDIT, ParserUtils.detectTransactionType("Rs 200 refund processed"))
    }

    @Test
    fun `detectTransactionType defaults to debit`() {
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Rs.500 transferred"))
    }

    @Test
    fun `extractMerchantAfterKeyword extracts merchant after 'to'`() {
        val result = ParserUtils.extractMerchantAfterKeyword(
            "Rs.350 debited to SWIGGY on 15-03-24",
            "to",
        )
        assertEquals("SWIGGY", result)
    }

    @Test
    fun `extractMerchantAfterKeyword extracts merchant after 'at'`() {
        val result = ParserUtils.extractMerchantAfterKeyword(
            "Rs.500 spent at DOMINOS via UPI",
            "at",
        )
        assertEquals("DOMINOS", result)
    }

    @Test
    fun `extractMerchantAfterKeyword returns null when keyword not found`() {
        val result = ParserUtils.extractMerchantAfterKeyword(
            "Rs.500 debited from account",
            "to",
        )
        assertNull(result)
    }

    @Test
    fun `extractMerchantFromUpi extracts from UPI reference`() {
        val result = ParserUtils.extractMerchantFromUpi("UPI/123456789/ZOMATO/15-03-24")
        assertNotNull(result)
        assertTrue(result!!.contains("ZOMATO", ignoreCase = true))
    }

    @Test
    fun `senderContainsAny matches case insensitive`() {
        assertTrue(ParserUtils.senderContainsAny("AD-HDFCBK", "HDFC", "SBI"))
        assertTrue(ParserUtils.senderContainsAny("ad-hdfcbk", "HDFC"))
        assertTrue(!ParserUtils.senderContainsAny("AD-SBIINB", "HDFC", "ICICI"))
    }

    @Test
    fun `looksLikeTransactionSms identifies transaction SMS`() {
        assertTrue(ParserUtils.looksLikeTransactionSms("Rs.500 debited from account"))
        assertTrue(ParserUtils.looksLikeTransactionSms("Rs 1000 credited to account"))
        assertTrue(ParserUtils.looksLikeTransactionSms("Paid Rs 200 via UPI"))
        assertTrue(ParserUtils.looksLikeTransactionSms("Rs.300 sent to John"))
        assertTrue(ParserUtils.looksLikeTransactionSms("Rs 750 received from ACME"))
        assertTrue(ParserUtils.looksLikeTransactionSms("Txn: Rs.250 spent at store"))
        assertTrue(ParserUtils.looksLikeTransactionSms("Transaction of Rs 900 completed"))
    }

    @Test
    fun `looksLikeTransactionSms rejects non-transaction SMS`() {
        assertTrue(!ParserUtils.looksLikeTransactionSms("Your OTP is 123456"))
        assertTrue(!ParserUtils.looksLikeTransactionSms("Your order has been shipped"))
        assertTrue(!ParserUtils.looksLikeTransactionSms("Flash sale today!"))
    }

    // ---------------------------------------------------------------------
    // Regression: debit must win when refund wording coexists with a debit
    // (failed UPI/card SMS: "...debited... refund will be initiated").
    // ---------------------------------------------------------------------
    @Test
    fun `detectTransactionType prefers debit over refund wording`() {
        assertEquals(
            TransactionType.DEBIT,
            ParserUtils.detectTransactionType("Rs.500 debited from a/c. Failed txn, refund will be initiated"),
        )
        assertEquals(
            TransactionType.DEBIT,
            ParserUtils.detectTransactionType("Rs 1,200 paid at store. Amount will be credited back on reversal"),
        )
    }

    @Test
    fun `detectTransactionType credit words still detected without debit`() {
        assertEquals(TransactionType.CREDIT, ParserUtils.detectTransactionType("Rs.5,000 credited to a/c XX1234"))
        assertEquals(TransactionType.CREDIT, ParserUtils.detectTransactionType("Rs.250 refund processed to your account"))
        assertEquals(TransactionType.CREDIT, ParserUtils.detectTransactionType("Rs.10,000 deposited in your account"))
    }

    @Test
    fun `detectTransactionType ignores substrings inside larger words`() {
        // "unpaid"/"repaid" must not flip type; no real verb -> default DEBIT.
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Rs.100 unpaid EMI reminder"))
    }

    // ---------------------------------------------------------------------
    // Regression: OTP authorization requests WITH an amount are not txns.
    // ---------------------------------------------------------------------
    @Test
    fun `looksLikeTransactionSms rejects OTP request containing an amount`() {
        assertTrue(
            !ParserUtils.looksLikeTransactionSms("Use OTP 4821 to approve txn of Rs.9,999"),
        )
        assertTrue(
            !ParserUtils.looksLikeTransactionSms("Your one time password is 5533 for transaction of INR 499"),
        )
    }

    @Test
    fun `looksLikeTransactionSms accepts real transaction bodies`() {
        assertTrue(ParserUtils.looksLikeTransactionSms("Rs.9,999 spent on card XX1234 at AMAZON"))
        assertTrue(!ParserUtils.looksLikeTransactionSms("Enter OTP 4821 to login"))
    }

    // ---------------------------------------------------------------------
    // parseAmount anchoring + number-format support
    // ---------------------------------------------------------------------
    @Test
    fun `parseAmount skips quoted balance and takes verb-adjacent amount`() {
        val amount = ParserUtils.parseAmount("Avl limit Rs.50,000. Spent Rs.2,000 at AMAZON")
        assertEquals(2000.0, amount!!, 0.01)
    }

    @Test
    fun `parseAmount skips trailing balance after txn amount`() {
        val amount = ParserUtils.parseAmount(
            "Rs.2,350.50 debited on 20-06-24 to FLIPKART. Avl Bal Rs.18,000.00",
        )
        assertEquals(2350.50, amount!!, 0.01)
    }

    @Test
    fun `parseAmount handles verb before currency`() {
        assertEquals(1500.0, ParserUtils.parseAmount("Your a/c is debited for Rs 1,500.00 today")!!, 0.01)
    }

    @Test
    fun `parseAmount supports Indian lakh grouping`() {
        assertEquals(123450.0, ParserUtils.parseAmount("Rs.1,23,450 debited")!!, 0.01)
        assertEquals(1234567.89, ParserUtils.parseAmount("₹12,34,567.89 spent")!!, 0.01)
        assertEquals(50000.0, ParserUtils.parseAmount("credited by Rs.50,000.00")!!, 0.01)
    }

    @Test
    fun `parseAmount supports western grouping`() {
        assertEquals(100000.0, ParserUtils.parseAmount("Rs 100,000 debited from account")!!, 0.01)
    }

    @Test
    fun `parseAmount falls back to first non-balance amount`() {
        val amount = ParserUtils.parseAmount("Statement shows outstanding Rs.5,000 and new spends Rs.750")
        assertEquals(750.0, amount!!, 0.01)
    }

    // ---------------------------------------------------------------------
    // Merchant extraction edge cases
    // ---------------------------------------------------------------------
    @Test
    fun `extractMerchantAfterKeyword captures merchant at end of string`() {
        val result = ParserUtils.extractMerchantAfterKeyword("Rs.350 debited to SWIGGY", "to")
        assertEquals("SWIGGY", result)
    }

    @Test
    fun `extractMerchantAfterKeyword does not match keyword inside another word`() {
        assertNull(ParserUtils.extractMerchantAfterKeyword("Rs.350 credited into ZOMATO wallet", "to"))
    }

    @Test
    fun `extractMerchantAfterKeyword terminator respects word boundaries`() {
        // "at" inside "ATM" must not terminate the capture.
        val result = ParserUtils.extractMerchantAfterKeyword("Rs.500 withdrawn at ATM MACHINE", "at")
        assertEquals("ATM MACHINE", result)
    }
}