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
}
