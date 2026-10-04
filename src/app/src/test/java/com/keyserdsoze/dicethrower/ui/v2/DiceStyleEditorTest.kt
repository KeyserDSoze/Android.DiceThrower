package com.keyserdsoze.dicethrower.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiceStyleEditorTest {
    @Test
    fun hexColorsRoundTripAsOpaqueArgb() {
        val color = parseDiceColor("#12aBcF")

        assertEquals(0xFF12ABCF.toInt(), color)
        assertEquals("#12ABCF", formatDiceColor(color!!))
    }

    @Test
    fun invalidHexColorsAreRejected() {
        assertNull(parseDiceColor("#12345"))
        assertNull(parseDiceColor("#GG0000"))
        assertNull(parseDiceColor("12345678"))
    }

    @Test
    fun duplicateNamesAdvanceWithoutColliding() {
        val next = nextCopyName(
            existingNames = listOf("Arcane", "Arcane Copy", "arcane copy 2"),
            sourceName = "Arcane",
            suffix = "Copy",
        )

        assertEquals("Arcane Copy 3", next)
    }
}
