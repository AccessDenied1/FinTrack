package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IciciBankParserTest {

    private lateinit var parser: IciciBankParser

    @Before
    fun setup() {
        parser = IciciBankParser()
    }

    @Test
    fun `canParse returns true for ICICI SMS`() {
        val sms = RawSms(
            sender = "AD-ICICIB",
            body = "Your Acct XX1234 is debited with Rs.800.00 on 15-Mar-24",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(parser.canParse(sms))
    }

    @Test
    fun `canParse returns false for non-ICICI sender`() {
        val sms = RawSms(
            sender = "AD-HDFCBK",
            body = "Your Acct debited with Rs.800.00",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(!parser.canParse(sms))
    }

    @Test
    fun `parse extracts debit transaction`() {
        val sms = RawSms(
            sender = "AD-ICICIB",
            body = "Your Acct XX5678 is debited with Rs.1,500.00 on 20-Jun-24 to UBER. Avl Bal Rs.22,000.00",
            timestamp = 1718880000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(1500.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("ICICI", result.bank)
    }

    @Test
    fun `parse extracts credit transaction`() {
        val sms = RawSms(
            sender = "AD-ICICIB",
            body = "Rs.50,000.00 credited to your Acct XX1234 on 01-Jul-24. Avl Bal Rs.1,20,000.00",
            timestamp = 1719792000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(50000.0, result!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, result.type)
    }

    @Test
    fun `parse returns null for promotional SMS`() {
        val sms = RawSms(
            sender = "AD-ICICIB",
            body = "Get instant personal loan up to Rs.10 lakh. Apply now on iMobile.",
            timestamp = System.currentTimeMillis(),
        )
        assertNull(parser.parse(sms))
    }
}
