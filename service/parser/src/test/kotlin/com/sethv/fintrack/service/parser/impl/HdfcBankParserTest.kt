package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HdfcBankParserTest {

    private lateinit var parser: HdfcBankParser

    @Before
    fun setup() {
        parser = HdfcBankParser()
    }

    @Test
    fun `canParse returns true for HDFC debit SMS`() {
        val sms = RawSms(
            sender = "AD-HDFCBK",
            body = "Rs 2,500.00 debited from a/c *1234 on 15-03-24 to SWIGGY. Avl bal Rs 45,000.00",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(parser.canParse(sms))
    }

    @Test
    fun `canParse returns false for non-HDFC sender`() {
        val sms = RawSms(
            sender = "AD-SBIINB",
            body = "Rs 2,500.00 debited from a/c *1234",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(!parser.canParse(sms))
    }

    @Test
    fun `parse extracts debit transaction correctly`() {
        val sms = RawSms(
            sender = "AD-HDFCBK",
            body = "Rs 350.00 debited from a/c *5678 on 20-06-24 to SWIGGY via UPI. Avl bal Rs 12,000.00",
            timestamp = 1718880000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(350.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("HDFC", result.bank)
        assertTrue(result.merchant.contains("SWIGGY", ignoreCase = true))
    }

    @Test
    fun `parse extracts credit transaction correctly`() {
        val sms = RawSms(
            sender = "AD-HDFCBK",
            body = "Rs 15,000.00 credited to a/c *1234 on 01-07-24. Avl bal Rs 60,000.00",
            timestamp = 1719792000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(15000.0, result!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, result.type)
    }

    @Test
    fun `parse returns null for non-transaction SMS from HDFC`() {
        val sms = RawSms(
            sender = "AD-HDFCBK",
            body = "Dear customer, your OTP for online transaction is 123456.",
            timestamp = System.currentTimeMillis(),
        )
        assertNull(parser.parse(sms))
    }

    @Test
    fun `parse handles amount without decimals`() {
        val sms = RawSms(
            sender = "AD-HDFCBK",
            body = "Rs 1,200 debited from a/c *9876 on 25-06-24 to Amazon. Avl bal Rs 8,000",
            timestamp = System.currentTimeMillis(),
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(1200.0, result!!.amount, 0.01)
    }
}
