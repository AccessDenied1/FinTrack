package com.sethv.fintrack.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BankColorTest {

    @Test
    fun `known banks map to distinct inks`() {
        assertNotEquals(bankColor("HDFC"), bankColor("ICICI"))
        assertNotEquals(bankColor("HDFC"), bankColor("SBI"))
        assertNotEquals(bankColor("HDFC"), bankColor("Axis"))
        assertNotEquals(bankColor("ICICI"), bankColor("SBI"))
        assertNotEquals(bankColor("ICICI"), bankColor("Axis"))
        assertNotEquals(bankColor("SBI"), bankColor("Axis"))
    }

    @Test
    fun `mapping is case and whitespace insensitive`() {
        assertEquals(bankColor("HDFC"), bankColor("hdfc bank"))
        assertEquals(bankColor("ICICI"), bankColor("  Icici  "))
        assertEquals(bankColor("SBI"), bankColor("SBI card"))
        assertEquals(bankColor("Axis"), bankColor("axis"))
    }

    @Test
    fun `unknown bank falls back to the neutral ink`() {
        assertEquals(bankColor("Unknown"), bankColor(""))
        assertEquals(bankColor("Unknown"), bankColor("Kotak"))
    }
}
