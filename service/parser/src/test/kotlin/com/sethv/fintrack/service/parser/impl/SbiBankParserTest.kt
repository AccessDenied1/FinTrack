package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SbiBankParserTest {

    private lateinit var parser: SbiBankParser

    @Before
    fun setup() {
        parser = SbiBankParser()
    }

    @Test
    fun `canParse returns true for SBI SMS`() {
        val sms = RawSms(
            sender = "AD-SBIINB",
            body = "Your a/c no. XXXXXXXX1234 is debited for Rs.500.00 on 15-03-24",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(parser.canParse(sms))
    }

    @Test
    fun `canParse returns false for non-SBI sender`() {
        val sms = RawSms(
            sender = "AD-HDFCBK",
            body = "Your a/c debited for Rs.500.00",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(!parser.canParse(sms))
    }

    @Test
    fun `parse extracts debit transaction`() {
        val sms = RawSms(
            sender = "AD-SBIINB",
            body = "Your a/c no. XXXXXXXX5678 is debited for Rs.2,350.50 on 20-06-24 to FLIPKART. Avl Bal Rs.18,000.00",
            timestamp = 1718880000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(2350.50, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("SBI", result.bank)
    }

    @Test
    fun `parse extracts credit transaction`() {
        val sms = RawSms(
            sender = "AD-SBIINB",
            body = "Your a/c no. XXXXXXXX1234 is credited by Rs.25,000.00 on 01-07-24. Avl Bal Rs.75,000.00",
            timestamp = 1719792000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(25000.0, result!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, result.type)
    }

    @Test
    fun `parse returns null for OTP message`() {
        val sms = RawSms(
            sender = "AD-SBIINB",
            body = "Your OTP is 456789 for SBI YONO login",
            timestamp = System.currentTimeMillis(),
        )
        assertNull(parser.parse(sms))
    }
}
