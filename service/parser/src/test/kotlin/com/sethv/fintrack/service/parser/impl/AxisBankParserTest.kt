package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AxisBankParserTest {

    private lateinit var parser: AxisBankParser

    @Before
    fun setup() {
        parser = AxisBankParser()
    }

    @Test
    fun `canParse returns true for Axis Bank SMS`() {
        val sms = RawSms(
            sender = "AD-AxisBk",
            body = "Rs.1,000.00 debited from A/c no. XX1234 on 15-Mar-24",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(parser.canParse(sms))
    }

    @Test
    fun `canParse returns false for non-Axis sender`() {
        val sms = RawSms(
            sender = "AD-SBIINB",
            body = "Rs.1,000.00 debited from A/c no. XX1234",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(!parser.canParse(sms))
    }

    @Test
    fun `parse extracts debit transaction`() {
        val sms = RawSms(
            sender = "AD-AxisBk",
            body = "Rs.3,200.00 debited from A/c no. XX5678 on 20-Jun-24 to MYNTRA. Avl Bal: Rs.45,000.00",
            timestamp = 1718880000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(3200.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("Axis", result.bank)
    }

    @Test
    fun `parse extracts credit transaction`() {
        val sms = RawSms(
            sender = "AD-AxisBk",
            body = "Rs.8,500.00 credited to A/c no. XX1234 on 01-Jul-24. Avl Bal: Rs.55,000.00",
            timestamp = 1719792000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(8500.0, result!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, result.type)
    }

    @Test
    fun `parse returns null for non-transaction SMS`() {
        val sms = RawSms(
            sender = "AD-AxisBk",
            body = "Your Axis Bank Credit Card statement is ready. Login to view.",
            timestamp = System.currentTimeMillis(),
        )
        assertNull(parser.parse(sms))
    }
}
