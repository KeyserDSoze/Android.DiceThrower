package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CancellationException

class CharacterFileReaderTest {
    private val data = AppData(characters = listOf(CharacterProfile("hero", "Mágus 日本語")))
    private val json get() = CharacterTransferCodec.encodeJson(data, "hero")
    private class Tracked(bytes: ByteArray, private val chunk: Int = 4096) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, minOf(length, chunk))
        override fun close() { closed = true; super.close() }
    }

    @Test fun smallChunksAndUnicodeUseTheExistingCodecAndCloseTheStream() {
        val stream = Tracked(json.toByteArray(Charsets.UTF_8), 3)
        assertEquals(CharacterTransferCodec.decodeJson(json), CharacterFileReader.decode(stream))
        assertTrue(stream.closed)
    }

    @Test fun exactlyThePortableByteLimitRemainsAccepted() {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val stream = Tracked(bytes + ByteArray(CharacterTransferCodec.MAX_RAW_BYTES - bytes.size) { 32 })
        assertEquals(CharacterTransferCodec.decodeJson(json), CharacterFileReader.decode(stream))
        assertTrue(stream.closed)
    }

    @Test fun oneByteOverTheLimitIsRejectedAndClosed() {
        val stream = Tracked(ByteArray(CharacterTransferCodec.MAX_RAW_BYTES + 1) { 32 })
        val failure = runCatching { CharacterFileReader.decode(stream) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("too large"))
        assertTrue(stream.closed)
    }

    @Test fun malformedJsonIsRejectedAndClosed() {
        val stream = Tracked("{".toByteArray())
        assertTrue(runCatching { CharacterFileReader.decode(stream) }.isFailure)
        assertTrue(stream.closed)
    }

    @Test fun readFailureStillClosesTheStream() {
        var closed = false
        val stream = object : java.io.InputStream() {
            override fun read(): Int = throw IOException("Provider unavailable")
            override fun close() { closed = true }
        }
        assertTrue(runCatching { CharacterFileReader.decode(stream) }.exceptionOrNull() is IOException)
        assertTrue(closed)
    }

    @Test fun cancellationClosesWithoutDecoding() {
        val stream = Tracked(json.toByteArray())
        val failure = runCatching {
            CharacterFileReader.decode(stream) { throw CancellationException("Superseded") }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(stream.closed)
    }
}
