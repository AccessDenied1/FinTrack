package com.sethv.fintrack.core.ui.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `currency formats rupee with indian grouping`() {
        assertEquals("₹1,234.5", Format.currency(1234.5))
        assertEquals("₹1,234", Format.currency(1234.0))
        assertEquals("₹12,34,567.89", Format.currency(1234567.89))
    }

    @Test
    fun `signed currency keeps sign convention`() {
        assertEquals("+₹500", Format.currencySigned(500.0))
        assertEquals("-₹500", Format.currencySigned(-500.0))
    }

    @Test
    fun `format is safe under concurrent access`() {
        val pool = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val done = CountDownLatch(8)
        var failures = 0
        repeat(8) { _ ->
            pool.submit {
                try {
                    ready.countDown()
                    // Hammer the shared singleton from many threads at once.
                    ready.await()
                    repeat(5_000) {
                        val out = Format.currency(1234567.89)
                        check(out == "₹12,34,567.89") { "corrupt format: $out" }
                    }
                } catch (_: Throwable) {
                    failures++
                } finally {
                    done.countDown()
                }
            }
        }
        done.await(30, TimeUnit.SECONDS)
        pool.shutdown()
        assertEquals(0, failures)
    }
}
