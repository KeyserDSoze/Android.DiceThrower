package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonNestingGuardTest {
    private fun rejected(raw: String): Boolean =
        runCatching { JsonNestingGuard.requireDepthAtMost(raw) }.exceptionOrNull() is IllegalArgumentException

    @Test
    fun deeplyNestedLinkIsRejectedBeforeParsing() {
        // 128 KiB of '[' inflates within the link size limit from a few hundred bytes; Android's
        // org.json would recurse once per bracket and overflow the stack.
        val nested = "[".repeat(128 * 1024)
        val compressed = ByteArrayOutputStream().also { stream ->
            DeflaterOutputStream(stream).use { it.write(nested.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        assertTrue(compressed.size < 1024)
        val token = "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)

        val error = runCatching { CharacterTransferCodec.decodeToken(token) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("deeper than"))
    }

    @Test
    fun deeplyNestedFilesAndBackupsAreRejected() {
        val nested = "{\"data\":" + "[".repeat(5_000) + "]".repeat(5_000) + "}"

        val transfer = runCatching { CharacterTransferCodec.decodeJson(nested) }.exceptionOrNull()
        val backup = runCatching { AppBackupCodec.decode(nested) }.exceptionOrNull()

        assertTrue(transfer is IllegalArgumentException && transfer.message!!.contains("deeper than"))
        assertTrue(backup is IllegalArgumentException && backup.message!!.contains("deeper than"))
    }

    @Test
    fun depthLimitIsInclusive() {
        val limit = JsonNestingGuard.MAX_DEPTH
        assertTrue(!rejected("[".repeat(limit) + "]".repeat(limit)))
        assertTrue(rejected("[".repeat(limit + 1) + "]".repeat(limit + 1)))
        assertTrue(rejected("{\"a\":".repeat(limit + 1)))
    }

    @Test
    fun bracketsInsideStringsAndCommentsAreNotNesting() {
        val deep = "[".repeat(100)
        assertTrue(!rejected("{\"text\":\"$deep\"}"))
        assertTrue(!rejected("{'text':'$deep'}"))
        assertTrue(!rejected("{\"text\":\"\\\"$deep\"}"))
        assertTrue(!rejected("/* $deep */ {}"))
        assertTrue(!rejected("// $deep\n{}"))
        assertTrue(!rejected("# $deep\n{}"))
    }

    @Test
    fun quotesAndClosingBracketsCannotHideRealNesting() {
        val deep = "[".repeat(JsonNestingGuard.MAX_DEPTH + 1)
        // A quote inside a comment or inside the other kind of string does not start a string.
        assertTrue(rejected("/* \" */ $deep"))
        assertTrue(rejected("// '\n$deep"))
        assertTrue(rejected("# \"\n$deep"))
        assertTrue(rejected("[\"'\", $deep"))
        assertTrue(rejected("['\"', $deep"))
        // Closing brackets inside a string do not lower the depth.
        assertTrue(rejected("[".repeat(60) + "\"]]]]]]]]]]\"" + "[".repeat(10)))
    }

    @Test
    fun stackOverflowWhileParsingBecomesAnArgumentError() {
        val error = runCatching {
            JsonNestingGuard.parse<Unit>("{}") { throw StackOverflowError() }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.cause is StackOverflowError)
    }

    @Test
    fun ordinaryTransfersStillDecode() {
        val data = AppData(characters = listOf(CharacterProfile(id = "hero", name = "Hero", level = 3)))
        val token = CharacterTransferCodec.encodeToken(data, "hero")

        assertEquals("Hero", CharacterTransferCodec.decodeToken(token).characters.single().name)
    }
}
