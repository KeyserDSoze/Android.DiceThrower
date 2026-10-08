package com.keyserdsoze.dicethrower.ui.v2

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FantasyTableFramesV2Test {
    private val latestPresets = listOf(
        DiceTableTheme.TAVERN_WOOD,
        DiceTableTheme.DUNGEON_STONE,
        DiceTableTheme.ELVEN_GROVE,
        DiceTableTheme.FROZEN_REALM,
        DiceTableTheme.DESERT_RUINS,
        DiceTableTheme.ASTRAL_VOID,
    )

    private fun colorDistance(a: Int, b: Int): Int =
        kotlin.math.abs(Color.red(a) - Color.red(b)) +
            kotlin.math.abs(Color.green(a) - Color.green(b)) +
            kotlin.math.abs(Color.blue(a) - Color.blue(b))

    @Test
    fun sixPresetsHaveVisiblyInsetTableRimsAndDistinctThemeColors() {
        val rimSamples = mutableSetOf<Int>()
        latestPresets.forEach { theme ->
            val bitmap = FantasyTableArtwork.render(theme)
            try {
                assertEquals(1080, bitmap.width)
                assertEquals(1920, bitmap.height)

                // A raised rail at x=90 must read as a separate object from the
                // playable center, not blend into a full-bleed wallpaper.
                val rim = bitmap.getPixel(90, 960)
                val surface = bitmap.getPixel(250, 960)
                assertTrue(
                    "${theme.name}: tabletop border cannot be distinguished from the play surface",
                    colorDistance(rim, surface) > 24,
                )
                rimSamples += rim
            } finally {
                bitmap.recycle()
            }

            val preview = FantasyTableArtwork.render(theme, preview = true)
            try {
                assertEquals(540, preview.width)
                assertEquals(960, preview.height)
                assertTrue(
                    "${theme.name}: preview lost its tabletop rim",
                    colorDistance(preview.getPixel(45, 480), preview.getPixel(125, 480)) > 24,
                )
            } finally {
                preview.recycle()
            }
        }
        // Shared framing geometry must not mean identical recolors.
        assertEquals("Each table must keep its own distinct material", latestPresets.size, rimSamples.size)
    }
}
