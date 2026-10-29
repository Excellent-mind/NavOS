package com.excellentmindblow.navos.signs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignColourTest {
    @Test
    fun expresswayCodesAreGreen() {
        assertTrue(SignColour.isExpressway("E1"))
        assertTrue(SignColour.isExpressway("E33"))
        assertTrue(SignColour.isExpressway("E 23"))
        assertTrue(SignColour.isExpressway("e11"))
        assertTrue(SignColour.isExpressway("FT2;E33"))
    }

    @Test
    fun otherRoadsAreBlue() {
        assertFalse(SignColour.isExpressway(null))
        assertFalse(SignColour.isExpressway(""))
        assertFalse(SignColour.isExpressway("2"))
        assertFalse(SignColour.isExpressway("FT2"))
        assertFalse(SignColour.isExpressway("B15"))
        assertFalse(SignColour.isExpressway("Jalan Ehsan"))
    }
}
