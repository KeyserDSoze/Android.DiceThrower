package com.keyserdsoze.dicethrower.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class CachedBitmapsTest {
    @Test
    fun sampleSizeNeverDecodesBelowTheDisplayedSize() {
        // 4000x3000 photo in a 176 px avatar: 1/16 still leaves 250x187 pixels, 1/32 would not.
        assertEquals(16, sampleSizeFor(4000, 3000, 176, 176))
        // 4096x2160 custom table in a 1080x568 preview card.
        assertEquals(2, sampleSizeFor(4096, 2160, 1080, 568))
        assertEquals(1, sampleSizeFor(2048, 2048, 1080, 568))
        // Images already smaller than the target are never upscaled or subsampled.
        assertEquals(1, sampleSizeFor(100, 100, 176, 176))
        assertEquals(1, sampleSizeFor(0, 0, 176, 176))
    }
}
