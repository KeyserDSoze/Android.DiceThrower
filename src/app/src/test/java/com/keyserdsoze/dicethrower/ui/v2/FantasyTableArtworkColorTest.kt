package com.keyserdsoze.dicethrower.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class FantasyTableArtworkColorTest {
    @Test
    fun rgbHexColorsAreOpaqueAndExplicitAlphaIsKept() {
        // Background gradients, e.g. the tavern's 0x513018 -> 0x251510, must be fully opaque.
        assertEquals(0xFF513018L.toInt(), FantasyTableArtwork.colorOf(0x513018))
        assertEquals(0xFF, FantasyTableArtwork.colorOf(0x251510) ushr 24)
        assertEquals(0xFF000000L.toInt(), FantasyTableArtwork.colorOf(0x000000))
        assertEquals(0xFFFFFFFFL.toInt(), FantasyTableArtwork.colorOf(0xFFFFFF))
        // Translucent glow and vignette colours spell out their alpha.
        assertEquals(0x88FFAF45L.toInt(), FantasyTableArtwork.colorOf(0x88FFAF45))
        assertEquals(0x22, FantasyTableArtwork.colorOf(0x22EE7520) ushr 24)
        assertEquals(0xAA, FantasyTableArtwork.colorOf(0xAA03070F) ushr 24)
    }
}
