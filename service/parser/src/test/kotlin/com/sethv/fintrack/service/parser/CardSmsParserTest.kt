package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.RawSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CardSmsParserTest {

    private lateinit var parser: CardSmsParser

    // Fixed "now" so year-less due-date inference is deterministic.
    private val smsTime: Long = LocalDate.of(2026, 8, 1).atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()

    @Before
    fun setup() {
        parser = CardSmsParser()
    }

    private fun sms(body: String) = RawSms(sender = "AD-HDFCBK", body = body, timestamp = smsTime)

    // ------------------------------------------------------------------
    // Bill / statement parsing
    // ------------------------------------------------------------------

    @Test
    fun `parses HDFC style statement with min due and dd-MMM-yy date`() {
        val result = parser.parseBill(
            sms("Statement generated for Card XX4521. Total Due Rs 45,000.00, Min Due Rs 2,250.00. Due Date: 18-Aug-26"),
        )

        assertNotNull(result)
        assertEquals("4521", result!!.cardLastFour)
        assertEquals(45000.0, result.totalDue, 0.01)
        assertEquals(2250.0, result.minDue!!, 0.01)
        assertEquals(LocalDate.of(2026, 8, 18).toEpochDay(), toLocalDate(result.dueDate).toEpochDay())
        assertEquals("HDFC", result.bankHint)
    }

    @Test
    fun `parses ICICI phrasing with slash date and no min due`() {
        val icici = RawSms(
            sender = "AD-ICICIB",
            body = "Your ICICI credit card XX8877 statement: Total Amount Due is INR 12,340.50, payment due date 22/08/2026",
            timestamp = smsTime,
        )
        val result = parser.parseBill(icici)

        assertNotNull(result)
        assertEquals("8877", result!!.cardLastFour)
        assertEquals(12340.50, result.totalDue, 0.01)
        assertNull(result.minDue)
        assertEquals(LocalDate.of(2026, 8, 22).toEpochDay(), toLocalDate(result.dueDate).toEpochDay())
    }

    @Test
    fun `parses payment received confirmation`() {
        val payment = parser.parsePayment(
            RawSms(
                sender = "AD-HDFCBK",
                body = "Payment of Rs.45,000.00 received for HDFC Card XX4521 on 16-Aug-26. Thank you!",
                timestamp = smsTime,
            ),
        )

        assertNotNull(payment)
        assertEquals("4521", payment!!.cardLastFour)
        assertEquals(45000.0, payment.amount, 0.01)
    }

    @Test
    fun `infers nearest occurrence for month-name dates without year`() {
        val result = parser.parseBill(sms("Card XX1111 total due Rs 5,000. Bill due date 15 Aug"))
        assertNotNull(result)
        // Aug 15 of the SMS year.
        assertEquals(LocalDate.of(2026, 8, 15).toEpochDay(), toLocalDate(result!!.dueDate).toEpochDay())
    }

    @Test
    fun `rejects plain transaction SMS`() {
        assertNull(parser.parseBill(sms("Rs.350 debited from a/c XX4521 to SWIGGY")))
    }

    @Test
    fun `rejects payment confirmations as bills`() {
        assertNull(
            parser.parseBill(sms("Payment received for card XX4521. Payment of Rs.45,000 credited.")),
        )
    }

    @Test
    fun `rejects bill without a parseable due date`() {
        assertNull(parser.parseBill(sms("Card XX4521 statement: total due Rs 9,000. Pay via iMobile app today!")))
    }

    @Test
    fun `payment parse rejects statement SMS`() {
        assertNull(parser.parsePayment(sms("Statement XX4521 total due Rs 45,000 due 18-Aug")))
    }

    // ------------------------------------------------------------------
    // Task 2: limit + period extraction
    // ------------------------------------------------------------------

    @Test
    fun `parses available limit and statement period`() {
        val raw = RawSms(
            sender = "AD-HDFCBK",
            body = "Available limit Rs 1,00,000 Outstanding Rs 42,000 Statement period 15 Oct - 14 Nov Total Due Rs 5,000 Due Date 05 Dec Card XX4521",
            timestamp = 1_700_000_000_000L,
        )
        val bill = parser.parseBill(raw)!!
        assertNotNull(bill)
        assertEquals(100000.0, bill.creditLimit!!, 0.01)
        assertTrue(bill.statementStart > 0)
    }

    @Test
    fun `no limit returns null`() {
        val raw = RawSms(
            sender = "AD-HDFCBK",
            body = "Total Due Rs 5,000 Due Date 05 Dec Card XX1234",
            timestamp = 1_700_000_000_000L,
        )
        val bill = parser.parseBill(raw)!!
        assertNotNull(bill)
        assertEquals(null, bill.creditLimit)
    }

    @Test
    fun `statement period fallback is dueDate minus 30 days when missing`() {
        val raw = RawSms(
            sender = "AD-HDFCBK",
            body = "Card XX1234 Total Due Rs 5,000 Due Date 05 Dec",
            timestamp = 1_700_000_000_000L,
        )
        val bill = parser.parseBill(raw)!!
        assertNotNull(bill)
        // fallback: 30 * DAY_MILLIS before dueDate
        val expected = bill.dueDate - 30L * 24 * 60 * 60 * 1000
        assertEquals(expected, bill.statementStart)
    }

    private fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}