package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GenericTransactionParserTest {

    private val parser = GenericTransactionParser()

    @Test
    fun `matches any SMS with amount and debit keyword`() {
        val sms = RawSms(
            sender = "AMAZON",
            body = "Rs.499 debited for order #12345",
            timestamp = 1_700_000_000_000L,
        )
        val parsed = parser.parse(sms)

        assertNotNull(parsed)
        assertEquals(499.0, parsed!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, parsed.type)
        assertEquals("AMAZON", parsed.bank)
    }

    @Test
    fun `matches rupee symbol SMS`() {
        val sms = RawSms(
            sender = "PAYTM",
            body = "₹250 sent to merchant via UPI",
            timestamp = 1_700_000_000_000L,
        )
        val parsed = parser.parse(sms)
        assertNotNull(parsed)
        assertEquals(250.0, parsed!!.amount, 0.01)
    }

    @Test
    fun `detects credit from received keyword`() {
        val sms = RawSms(
            sender = "PAYTM",
            body = "Rs.1000 received in wallet",
            timestamp = 1_700_000_000_000L,
        )
        val parsed = parser.parse(sms)
        assertNotNull(parsed)
        assertEquals(TransactionType.CREDIT, parsed!!.type)
    }

    @Test
    fun `returns null when no amount found`() {
        val sms = RawSms(
            sender = "AMAZON",
            body = "Order shipped successfully",
            timestamp = 1_700_000_000_000L,
        )
        assertNull(parser.parse(sms))
    }

    @Test
    fun `extracts merchant after to`() {
        val sms = RawSms(
            sender = "JIO",
            body = "Rs.599 paid to NETFLIX via UPI",
            timestamp = 1_700_000_000_000L,
        )
        val parsed = parser.parse(sms)
        assertNotNull(parsed)
        assertEquals("NETFLIX", parsed!!.merchant)
    }

    @Test
    fun `falls back to Unknown Merchant when no keyword matches`() {
        val sms = RawSms(
            sender = "WEIRDSRC",
            body = "Rs 100 debited from wallet",
            timestamp = 1_700_000_000_000L,
        )
        val parsed = parser.parse(sms)
        assertNotNull(parsed)
        assertEquals("Unknown Merchant", parsed!!.merchant)
    }

    @Test
    fun `truncates long sender labels`() {
        val sms = RawSms(
            sender = "VERY-LONG-SENDER-ID-1234567890",
            body = "Rs.50 debited from account",
            timestamp = 1_700_000_000_000L,
        )
        val parsed = parser.parse(sms)
        assertNotNull(parsed)
        // Label is capped at 16 chars; should not be the full string.
        assertEquals(true, parsed!!.bank.length <= 16)
    }
}