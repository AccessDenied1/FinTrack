package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenericUpiParserTest {

    private lateinit var parser: GenericUpiParser

    @Before
    fun setup() {
        parser = GenericUpiParser()
    }

    @Test
    fun `canParse returns true for UPI debit SMS`() {
        val sms = RawSms(
            sender = "AD-UPIBNK",
            body = "Rs.200 debited from A/c linked to VPA user@upi on 15-03-24. UPI Ref 123456789",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(parser.canParse(sms))
    }

    @Test
    fun `canParse returns true for paid SMS`() {
        val sms = RawSms(
            sender = "VM-JIOBNK",
            body = "Paid Rs 150 to CHAI POINT via UPI. UPI Ref: 345678901234",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(parser.canParse(sms))
    }

    @Test
    fun `canParse returns false for non-financial SMS`() {
        val sms = RawSms(
            sender = "AD-RANDOM",
            body = "Your order has been shipped. Track at http://example.com",
            timestamp = System.currentTimeMillis(),
        )
        assertTrue(!parser.canParse(sms))
    }

    @Test
    fun `parse extracts UPI debit transaction`() {
        val sms = RawSms(
            sender = "AD-PAYTMB",
            body = "Rs.500 debited from A/c linked to VPA user@paytm on 20-06-24. UPI/234567890/ZOMATO",
            timestamp = 1718880000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("UPI", result.bank)
    }

    @Test
    fun `parse extracts paid-to transaction`() {
        val sms = RawSms(
            sender = "VM-GPAY",
            body = "Paid Rs 350 to SWIGGY via UPI. UPI Ref: 567890123456",
            timestamp = 1718880000000L,
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(350.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.type)
        assertTrue(result.merchant.contains("SWIGGY", ignoreCase = true))
    }

    @Test
    fun `parse handles INR prefix`() {
        val sms = RawSms(
            sender = "AD-PHONEPE",
            body = "INR 1,250.50 debited from your account via UPI. Ref: 789012345",
            timestamp = System.currentTimeMillis(),
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertEquals(1250.50, result!!.amount, 0.01)
    }

    @Test
    fun `parse returns null when amount cannot be extracted`() {
        val sms = RawSms(
            sender = "AD-UPIBNK",
            body = "UPI transaction failed. Please try again.",
            timestamp = System.currentTimeMillis(),
        )
        assertNull(parser.parse(sms))
    }

    @Test
    fun `parse extracts merchant from UPI reference`() {
        val sms = RawSms(
            sender = "AD-PAYTMB",
            body = "Rs.750 debited from your A/c. UPI/112233445566/DOMINOS/upi@ybl",
            timestamp = System.currentTimeMillis(),
        )
        val result = parser.parse(sms)

        assertNotNull(result)
        assertTrue(result!!.merchant.contains("DOMINOS", ignoreCase = true))
    }
}
