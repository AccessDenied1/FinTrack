package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CompositeSmsParserTest {

    private lateinit var compositeParser: CompositeSmsParser

    private val alwaysParseParser = object : SmsParser {
        override fun canParse(sms: RawSms): Boolean = true
        override fun parse(sms: RawSms): ParsedTransaction = ParsedTransaction(
            amount = 100.0,
            merchant = "AlwaysParsed",
            type = TransactionType.DEBIT,
            dateTime = sms.timestamp,
            bank = "TestBank",
            smsBody = sms.body,
        )
    }

    private val neverParseParser = object : SmsParser {
        override fun canParse(sms: RawSms): Boolean = false
        override fun parse(sms: RawSms): ParsedTransaction? = null
    }

    @Before
    fun setup() {
        compositeParser = CompositeSmsParser(linkedSetOf(neverParseParser, alwaysParseParser))
    }

    @Test
    fun `parse delegates to first matching parser`() {
        val sms = RawSms(sender = "TEST", body = "test sms", timestamp = 1000L)
        val result = compositeParser.parse(sms)

        assertNotNull(result)
        assertEquals("AlwaysParsed", result!!.merchant)
        assertEquals(100.0, result.amount, 0.01)
    }

    @Test
    fun `parse returns null when no parser matches`() {
        val onlyNeverParser = CompositeSmsParser(setOf(neverParseParser))
        val sms = RawSms(sender = "TEST", body = "test sms", timestamp = 1000L)

        assertNull(onlyNeverParser.parse(sms))
    }

    @Test
    fun `canParse returns true when any parser can parse`() {
        val sms = RawSms(sender = "TEST", body = "test sms", timestamp = 1000L)
        assertEquals(true, compositeParser.canParse(sms))
    }

    @Test
    fun `canParse returns false when no parser can parse`() {
        val onlyNeverParser = CompositeSmsParser(setOf(neverParseParser))
        val sms = RawSms(sender = "TEST", body = "test sms", timestamp = 1000L)
        assertEquals(false, onlyNeverParser.canParse(sms))
    }

    @Test
    fun `parsers are tried in order, first match wins`() {
        val firstParser = object : SmsParser {
            override fun canParse(sms: RawSms): Boolean = true
            override fun parse(sms: RawSms): ParsedTransaction = ParsedTransaction(
                amount = 1.0,
                merchant = "First",
                type = TransactionType.DEBIT,
                dateTime = sms.timestamp,
                bank = "Bank1",
                smsBody = sms.body,
            )
        }
        val secondParser = object : SmsParser {
            override fun canParse(sms: RawSms): Boolean = true
            override fun parse(sms: RawSms): ParsedTransaction = ParsedTransaction(
                amount = 2.0,
                merchant = "Second",
                type = TransactionType.DEBIT,
                dateTime = sms.timestamp,
                bank = "Bank2",
                smsBody = sms.body,
            )
        }

        val parser = CompositeSmsParser(linkedSetOf(firstParser, secondParser))
        val sms = RawSms(sender = "TEST", body = "test", timestamp = 1000L)
        val result = parser.parse(sms)

        assertEquals("First", result!!.merchant)
    }

    // ---------------------------------------------------------------------
    // Integration: the real parser chain must keep bank attribution for
    // "Paid/Sent" phrasing instead of letting the generic UPI parser claim it.
    // ---------------------------------------------------------------------
    private val productionChain = CompositeSmsParser(
        linkedSetOf(
            com.sethv.fintrack.service.parser.impl.HdfcBankParser(),
            com.sethv.fintrack.service.parser.impl.SbiBankParser(),
            com.sethv.fintrack.service.parser.impl.IciciBankParser(),
            com.sethv.fintrack.service.parser.impl.AxisBankParser(),
            com.sethv.fintrack.service.parser.impl.GenericUpiParser(),
            com.sethv.fintrack.service.parser.impl.GenericTransactionParser(),
        ),
    )

    @Test
    fun `production chain attributes SBI paid SMS to SBI not UPI`() {
        val sms = RawSms(
            sender = "AD-SBIINB",
            body = "Paid Rs.500 to AMAZON on 15-03-24. UPI Ref: 123456789012",
            timestamp = 1718880000000L,
        )
        val result = productionChain.parse(sms)
        assertEquals("SBI", result!!.bank)
    }

    @Test
    fun `production chain attributes ICICI paid SMS to ICICI not UPI`() {
        val sms = RawSms(
            sender = "AD-ICICIB",
            body = "Sent Rs.750 to SWIGGY via UPI. Ref 987654321098",
            timestamp = 1718880000000L,
        )
        val result = productionChain.parse(sms)
        assertEquals("ICICI", result!!.bank)
    }

    @Test
    fun `production chain falls back to sender label for wallet SMS without UPI`() {
        val sms = RawSms(
            sender = "VM-PAYTMB",
            body = "Rs.499 debited for wallet top-up order #12345",
            timestamp = 1718880000000L,
        )
        val result = productionChain.parse(sms)
        assertEquals("VM-PAYTMB", result!!.bank)
    }

    @Test
    fun `production chain rejects OTP authorization request with amount`() {
        val sms = RawSms(
            sender = "AD-HDFCBK",
            body = "Use OTP 4821 to approve txn of Rs.9,999",
            timestamp = 1718880000000L,
        )
        assertNull(productionChain.parse(sms))
    }
}
