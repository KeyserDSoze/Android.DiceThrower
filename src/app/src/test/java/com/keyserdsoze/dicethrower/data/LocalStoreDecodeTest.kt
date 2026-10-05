package com.keyserdsoze.dicethrower.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFails
import org.junit.Test

class LocalStoreDecodeTest {
    @Test
    fun corruptStoredDataIsNotSilentlyReplacedWithEmptyData() {
        assertFails { decodeStoredData("{not-json") }
    }

    @Test
    fun corruptStoredSettingsAreNotSilentlyReplacedWithDefaults() {
        assertFails { decodeStoredSettings("{not-json") }
    }

    @Test
    fun validEmptyStoredDataStillDecodesNormally() {
        val decoded = decodeStoredData(AppDataJsonCodec.encodeData(com.keyserdsoze.dicethrower.model.AppData()).toString())
        assertEquals(0, decoded.characters.size)
    }
}
