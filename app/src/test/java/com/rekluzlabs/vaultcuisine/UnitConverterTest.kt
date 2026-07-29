package com.rekluzlabs.vaultcuisine

import com.rekluzlabs.vaultcuisine.util.UnitConverter
import com.rekluzlabs.vaultcuisine.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {

    // ── canonicalization ──

    @Test
    fun `canonicalize cup variations`() {
        assertEquals("CUP", UnitConverter.canonicalizeUnit("cup")?.name)
        assertEquals("CUP", UnitConverter.canonicalizeUnit("cups")?.name)
        assertEquals("CUP", UnitConverter.canonicalizeUnit("CUP")?.name)
        assertEquals("CUP", UnitConverter.canonicalizeUnit("Cups")?.name)
    }

    @Test
    fun `canonicalize tbsp variations`() {
        assertEquals("TBSP", UnitConverter.canonicalizeUnit("tbsp")?.name)
        assertEquals("TBSP", UnitConverter.canonicalizeUnit("tablespoon")?.name)
        assertEquals("TBSP", UnitConverter.canonicalizeUnit("tablespoons")?.name)
        assertEquals("TBSP", UnitConverter.canonicalizeUnit("TBS")?.name)
    }

    @Test
    fun `canonicalize tsp`() {
        assertEquals("TSP", UnitConverter.canonicalizeUnit("tsp")?.name)
        assertEquals("TSP", UnitConverter.canonicalizeUnit("teaspoon")?.name)
        assertEquals("TSP", UnitConverter.canonicalizeUnit("teaspoons")?.name)
    }

    @Test
    fun `canonicalize metric volume`() {
        assertEquals("ML", UnitConverter.canonicalizeUnit("ml")?.name)
        assertEquals("ML", UnitConverter.canonicalizeUnit("milliliter")?.name)
        assertEquals("L", UnitConverter.canonicalizeUnit("l")?.name)
        assertEquals("L", UnitConverter.canonicalizeUnit("liter")?.name)
        assertEquals("L", UnitConverter.canonicalizeUnit("litres")?.name)
    }

    @Test
    fun `canonicalize weight`() {
        assertEquals("G", UnitConverter.canonicalizeUnit("g")?.name)
        assertEquals("G", UnitConverter.canonicalizeUnit("gram")?.name)
        assertEquals("KG", UnitConverter.canonicalizeUnit("kg")?.name)
        assertEquals("KG", UnitConverter.canonicalizeUnit("kilogram")?.name)
        assertEquals("OZ", UnitConverter.canonicalizeUnit("oz")?.name)
        assertEquals("OZ", UnitConverter.canonicalizeUnit("ounce")?.name)
        assertEquals("OZ", UnitConverter.canonicalizeUnit("ounces")?.name)
        assertEquals("LB", UnitConverter.canonicalizeUnit("lb")?.name)
        assertEquals("LB", UnitConverter.canonicalizeUnit("lbs")?.name)
        assertEquals("LB", UnitConverter.canonicalizeUnit("pound")?.name)
        assertEquals("LB", UnitConverter.canonicalizeUnit("pounds")?.name)
    }

    @Test
    fun `canonicalize fl oz`() {
        assertEquals("FL_OZ", UnitConverter.canonicalizeUnit("fl oz")?.name)
        assertEquals("FL_OZ", UnitConverter.canonicalizeUnit("fluid ounce")?.name)
        assertEquals("FL_OZ", UnitConverter.canonicalizeUnit("floz")?.name)
    }

    @Test
    fun `canonicalize returns null for count units`() {
        assertNull(UnitConverter.canonicalizeUnit("cloves"))
        assertNull(UnitConverter.canonicalizeUnit("pinch"))
        assertNull(UnitConverter.canonicalizeUnit("pinches"))
        assertNull(UnitConverter.canonicalizeUnit("cans"))
        assertNull(UnitConverter.canonicalizeUnit("pieces"))
        assertNull(UnitConverter.canonicalizeUnit(null))
        assertNull(UnitConverter.canonicalizeUnit(""))
    }

    @Test
    fun `canonicalize returns null for unknown units`() {
        assertNull(UnitConverter.canonicalizeUnit("slices"))
        assertNull(UnitConverter.canonicalizeUnit("bunch"))
        assertNull(UnitConverter.canonicalizeUnit("sprig"))
    }

    // ── classification ──

    @Test
    fun `classify known units`() {
        assertEquals("VOLUME", UnitConverter.classifyUnit("cup").name)
        assertEquals("VOLUME", UnitConverter.classifyUnit("tbsp").name)
        assertEquals("VOLUME", UnitConverter.classifyUnit("ml").name)
        assertEquals("WEIGHT", UnitConverter.classifyUnit("g").name)
        assertEquals("WEIGHT", UnitConverter.classifyUnit("oz").name)
        assertEquals("COUNT", UnitConverter.classifyUnit("cloves").name)
        assertEquals("COUNT", UnitConverter.classifyUnit(null).name)
        assertEquals("COUNT", UnitConverter.classifyUnit("").name)
    }

    // ── volume conversion: imperial → metric ──

    @Test
    fun `convert cups to ml`() {
        // 1 cup = 236.588 ml → rounded to nearest 10 → 240
        val result = UnitConverter.convertAndFormat("1", "cup", 1.0, UnitSystem.METRIC)
        assertEquals("240", result?.first)
        assertEquals("ml", result?.second)
    }

    @Test
    fun `convert 2 cups to ml`() {
        // 2 cups = 473.176 ml → rounded to nearest 10 → 470
        val result = UnitConverter.convertAndFormat("2", "cup", 1.0, UnitSystem.METRIC)
        assertEquals("470", result?.first)
        assertEquals("ml", result?.second)
    }

    @Test
    fun `convert tbsp to ml`() {
        // 1 tbsp = 14.787 ml → rounded to nearest 5 → 15
        val result = UnitConverter.convertAndFormat("1", "tbsp", 1.0, UnitSystem.METRIC)
        assertEquals("15", result?.first)
        assertEquals("ml", result?.second)
    }

    @Test
    fun `convert tsp to ml`() {
        // 1 tsp = 4.929 ml → rounded to nearest 5 → 5
        val result = UnitConverter.convertAndFormat("1", "tsp", 1.0, UnitSystem.METRIC)
        assertEquals("5", result?.first)
        assertEquals("ml", result?.second)
    }

    @Test
    fun `convert 1 oz to g`() {
        val result = UnitConverter.convertAndFormat("1", "oz", 1.0, UnitSystem.METRIC)
        assertEquals("30", result?.first)
        assertEquals("g", result?.second)
    }

    @Test
    fun `convert 1 lb to g`() {
        val result = UnitConverter.convertAndFormat("1", "lb", 1.0, UnitSystem.METRIC)
        assertEquals("455", result?.first)
        assertEquals("g", result?.second)
    }

    // ── volume conversion: metric → imperial ──

    @Test
    fun `convert ml to cups`() {
        // 236.588 ml / 236.588 = 1 cup
        val result = UnitConverter.convertAndFormat("236.588", "ml", 1.0, UnitSystem.IMPERIAL)
        assertEquals("1", result?.first)
        assertEquals("cup", result?.second)
    }

    @Test
    fun `convert 15 ml to tbsp`() {
        // 15 ml / 14.787 = 1.01 → 1 tbsp
        val result = UnitConverter.convertAndFormat("15", "ml", 1.0, UnitSystem.IMPERIAL)
        assertEquals("1", result?.first)
        assertEquals("tbsp", result?.second)
    }

    @Test
    fun `convert 300 g to oz`() {
        // 300 g → 0.66 lb → below 2.0 threshold → oz path: 300/28.35 = 10.58 → nearest 1/2 oz → 10.5
        val result = UnitConverter.convertAndFormat("300", "g", 1.0, UnitSystem.IMPERIAL)
        assertEquals("10 1/2", result?.first)
        assertEquals("oz", result?.second)
    }

    @Test
    fun `convert 500 g to oz`() {
        // 500 g → 1.10 lb → below 2.0 threshold → oz path: 500/28.35 = 17.64 → nearest 1/2 oz → 17.5
        val result = UnitConverter.convertAndFormat("500", "g", 1.0, UnitSystem.IMPERIAL)
        assertEquals("17 1/2", result?.first)
        assertEquals("oz", result?.second)
    }

    @Test
    fun `convert 1 kg to lbs`() {
        // 1 kg → 2.20 lb → above 2.0 threshold → lb path: nearest 1/4 lb → 2.25
        val result = UnitConverter.convertAndFormat("1", "kg", 1.0, UnitSystem.IMPERIAL)
        assertEquals("2 1/4", result?.first)
        assertEquals("lbs", result?.second)
    }

    @Test
    fun `convert 2 kg to lbs`() {
        // 2 kg → 4.41 lb → above 2.0 threshold → lb path: nearest 1/4 lb → 4.5
        val result = UnitConverter.convertAndFormat("2", "kg", 1.0, UnitSystem.IMPERIAL)
        assertEquals("4 1/2", result?.first)
        assertEquals("lbs", result?.second)
    }

    @Test
    fun `convert 30 g to oz`() {
        // 30 g / 28.35 = 1.058 → rounded to nearest 1/2 oz → 1
        val result = UnitConverter.convertAndFormat("30", "g", 1.0, UnitSystem.IMPERIAL)
        assertEquals("1", result?.first)
        assertEquals("oz", result?.second)
    }

    // ── conversion with scaling ──

    @Test
    fun `scale then convert cups to ml`() {
        // 1 cup * 2 = 2 cups = 473.176 ml → rounded to nearest 10 → 470
        val result = UnitConverter.convertAndFormat("1", "cup", 2.0, UnitSystem.METRIC)
        assertEquals("470", result?.first)
        assertEquals("ml", result?.second)
    }

    @Test
    fun `scale fraction then convert`() {
        // 1/2 cup * 3 = 1.5 cups = 354.882 ml → rounded to nearest 10 → 350
        val result = UnitConverter.convertAndFormat("1/2", "cup", 3.0, UnitSystem.METRIC)
        assertEquals("350", result?.first)
        assertEquals("ml", result?.second)
    }

    // ── unconvertible / edge cases ──

    @Test
    fun `null unit returns null`() {
        assertNull(UnitConverter.convertAndFormat("1", null, 1.0, UnitSystem.METRIC))
    }

    @Test
    fun `count unit returns null`() {
        assertNull(UnitConverter.convertAndFormat("3", "cloves", 1.0, UnitSystem.METRIC))
        assertNull(UnitConverter.convertAndFormat("1", "pinch", 1.0, UnitSystem.METRIC))
        assertNull(UnitConverter.convertAndFormat("2", "cans", 1.0, UnitSystem.METRIC))
    }

    @Test
    fun `as_written returns null`() {
        assertNull(UnitConverter.convertAndFormat("1", "cup", 1.0, UnitSystem.AS_WRITTEN))
    }

    @Test
    fun `same system returns null`() {
        assertNull(UnitConverter.convertAndFormat("1", "ml", 1.0, UnitSystem.METRIC))
        assertNull(UnitConverter.convertAndFormat("1", "cup", 1.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `unparseable amount returns null`() {
        assertNull(UnitConverter.convertAndFormat("a pinch", "cup", 1.0, UnitSystem.METRIC))
    }

    // ── range conversion ──

    @Test
    fun `convert range cups to ml`() {
        val result = UnitConverter.convertAndFormat("1-2", "cup", 1.0, UnitSystem.METRIC)
        assertEquals("240-470", result?.first)
        assertEquals("ml", result?.second)
    }
}
