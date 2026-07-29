package com.rekluzlabs.vaultcuisine

import com.rekluzlabs.vaultcuisine.util.AmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    // ── parseAmounts: integers ──

    @Test
    fun `parse plain integer`() {
        assertEquals(listOf(2.0), AmountParser.parseAmounts("2"))
    }

    @Test
    fun `parse zero`() {
        assertEquals(listOf(0.0), AmountParser.parseAmounts("0"))
    }

    // ── parseAmounts: decimals ──

    @Test
    fun `parse decimal`() {
        assertEquals(listOf(1.5), AmountParser.parseAmounts("1.5"))
    }

    @Test
    fun `parse decimal with leading zero`() {
        assertEquals(listOf(0.75), AmountParser.parseAmounts("0.75"))
    }

    // ── parseAmounts: simple fractions ──

    @Test
    fun `parse simple fraction`() {
        assertEquals(listOf(0.5), AmountParser.parseAmounts("1/2"))
    }

    @Test
    fun `parse third`() {
        val result = AmountParser.parseAmounts("1/3")
        assertEquals(1, result.size)
        assertEquals(1.0 / 3.0, result[0], 1e-10)
    }

    @Test
    fun `parse quarter`() {
        assertEquals(listOf(0.25), AmountParser.parseAmounts("1/4"))
    }

    @Test
    fun `parse three quarters`() {
        assertEquals(listOf(0.75), AmountParser.parseAmounts("3/4"))
    }

    // ── parseAmounts: mixed numbers ──

    @Test
    fun `parse mixed number`() {
        assertEquals(listOf(2.5), AmountParser.parseAmounts("2 1/2"))
    }

    @Test
    fun `parse mixed number with third`() {
        val result = AmountParser.parseAmounts("1 1/3")
        assertEquals(1, result.size)
        assertEquals(1.0 + 1.0 / 3.0, result[0], 1e-10)
    }

    // ── parseAmounts: ranges ──

    @Test
    fun `parse range integers`() {
        assertEquals(listOf(2.0, 3.0), AmountParser.parseAmounts("2-3"))
    }

    @Test
    fun `parse range with en-dash`() {
        assertEquals(listOf(1.0, 2.0), AmountParser.parseAmounts("1–2"))
    }

    @Test
    fun `parse range decimals`() {
        assertEquals(listOf(1.5, 2.5), AmountParser.parseAmounts("1.5-2.5"))
    }

    @Test
    fun `parse range fractions`() {
        assertEquals(listOf(0.5, 1.0), AmountParser.parseAmounts("1/2-1"))
    }

    // ── parseAmounts: unparseable ──

    @Test
    fun `unparseable returns empty for descriptive text`() {
        assertEquals(emptyList<Double>(), AmountParser.parseAmounts("a pinch"))
    }

    @Test
    fun `unparseable returns empty for to taste`() {
        assertEquals(emptyList<Double>(), AmountParser.parseAmounts("to taste"))
    }

    @Test
    fun `unparseable returns empty for empty string`() {
        assertEquals(emptyList<Double>(), AmountParser.parseAmounts(""))
    }

    @Test
    fun `unparseable returns empty for blank string`() {
        assertEquals(emptyList<Double>(), AmountParser.parseAmounts("  "))
    }

    @Test
    fun `unparseable returns empty for messy OCR`() {
        assertEquals(emptyList<Double>(), AmountParser.parseAmounts("l0l"))
    }

    @Test
    fun `unparseable with leading text returns empty`() {
        assertEquals(emptyList<Double>(), AmountParser.parseAmounts("about 2"))
    }

    // ── formatAmount ──

    @Test
    fun `format whole number`() {
        assertEquals("2", AmountParser.formatAmount(2.0))
    }

    @Test
    fun `format half`() {
        assertEquals("1/2", AmountParser.formatAmount(0.5))
    }

    @Test
    fun `format mixed number`() {
        assertEquals("1 1/2", AmountParser.formatAmount(1.5))
    }

    @Test
    fun `format quarter`() {
        assertEquals("1/4", AmountParser.formatAmount(0.25))
    }

    @Test
    fun `format three quarters`() {
        assertEquals("3/4", AmountParser.formatAmount(0.75))
    }

    @Test
    fun `format third`() {
        assertEquals("1/3", AmountParser.formatAmount(1.0 / 3.0))
    }

    @Test
    fun `format zero`() {
        assertEquals("0", AmountParser.formatAmount(0.0))
    }

    // ── scaleAndFormat ──

    @Test
    fun `scale integer by factor`() {
        assertEquals("4", AmountParser.scaleAndFormat("2", 2.0))
    }

    @Test
    fun `scale fraction by factor`() {
        assertEquals("1", AmountParser.scaleAndFormat("1/2", 2.0))
    }

    @Test
    fun `scale range by factor`() {
        assertEquals("4-6", AmountParser.scaleAndFormat("2-3", 2.0))
    }

    @Test
    fun `scale unparseable returns original`() {
        assertEquals("a pinch", AmountParser.scaleAndFormat("a pinch", 2.0))
    }

    @Test
    fun `scale half by three`() {
        assertEquals("1 1/2", AmountParser.scaleAndFormat("1/2", 3.0))
    }
}
