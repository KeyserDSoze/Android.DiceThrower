package com.keyserdsoze.dicethrower.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LocalStoreDecodeTest {
    @Test
    fun corruptStoredDataIsNotSilentlyReplacedWithEmptyData() {
        expectFailure { decodeStoredData("{not-json") }
    }

    @Test
    fun corruptStoredSettingsAreNotSilentlyReplacedWithDefaults() {
        expectFailure { decodeStoredSettings("{not-json") }
    }

    @Test
    fun validEmptyStoredDataStillDecodesNormally() {
        val decoded = decodeStoredData(AppDataJsonCodec.encodeData(com.keyserdsoze.dicethrower.model.AppData()).toString())
        assertEquals(0, decoded.characters.size)
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected decoding to fail")
        } catch (_: Exception) {
            // Expected: corrupt persistence must be surfaced, never replaced with defaults.
        }
    }
}
