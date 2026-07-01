package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
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
    fun `detectTransactionType identifies debit`() {
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Rs.500 debited from account"))
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Paid Rs 200 to merchant"))
        assertEquals(TransactionType.DEBIT, ParserUtils.detectTransactionType("Rs 100 spent at store"))
    }

    @Test
    fun `detectTransactionType identifies credit`() {
        assertEquals(TransactionType.CREDIT, ParserUtils.detectTransactionType("Rs.5000 credited to account"))
        assertEquals(TransactionType.CREDIT, ParserUtils.detectTransactionType("Rs 1000 received from person"))
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
    }

    @Test
    fun `looksLikeTransactionSms rejects non-transaction SMS`() {
        assertTrue(!ParserUtils.looksLikeTransactionSms("Your OTP is 123456"))
        assertTrue(!ParserUtils.looksLikeTransactionSms("Your order has been shipped"))
    }

    private fun assertNotNull(value: Any?) {
        org.junit.Assert.assertNotNull(value)
    }
}
