package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollSubgroup
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import com.google.zxing.BinaryBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter

class CharacterTransferCodecTest {
    private fun sample() = AppData(
        characters = listOf(CharacterProfile(id = "fighter", name = "Aric", level = 1, defaultDiceStyleId = "style-1")),
        modifiers = listOf(CharacterModifier("str", "fighter", "Strength", 3)),
        groups = listOf(RollGroup("combat", "fighter", "Combat")),
        diceStyles = listOf(DiceStyle("style-1", null, "Blue steel", primaryColorArgb = -1, secondaryColorArgb = -2)),
        rolls = listOf(RollDefinition(
            id = "attack", characterId = "fighter", name = "Longsword",
            expression = "(1d20+{Strength})", groupId = "combat",
            subgroups = listOf(RollSubgroup("attack-part", "Attack", "1d20+{Strength}")),
            diceAppearance = RollDiceAppearance(
                mode = DiceAppearanceMode.PER_DIE,
                subgroupStyleIds = mapOf("attack-part" to "style-1"),
            ),
        )),
    )

    private fun packet(data: AppData): JSONObject = JSONObject()
        .put("format", CharacterTransferCodec.FORMAT)
        .put("formatVersion", CharacterTransferCodec.FORMAT_VERSION)
        .put("data", AppDataJsonCodec.encodeDataForSync(data))

    @Test fun browserCompatibleZlibTokenRoundTrips() {
        val json = packet(sample()).toString()
        val compressed = ByteArrayOutputStream().also { stream ->
            DeflaterOutputStream(stream).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val token = "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        assertEquals(sample(), CharacterTransferCodec.decodeToken(token))
    }

    @Test fun importTwiceDoesNotOverwriteAndRemapsPartsAndStyles() {
        val inbound = CharacterTransferCodec.decodeJson(packet(sample()).toString())
        val existing = AppData(characters = listOf(CharacterProfile("existing", "Current")))
        val once = CharacterTransferCodec.importCopy(existing, inbound)
        val twice = CharacterTransferCodec.importCopy(once, inbound)
        assertEquals(3, twice.characters.size)
        val first = twice.characters[1]; val second = twice.characters[2]
        assertNotEquals(first.id, second.id)
        assertEquals("Current", twice.characters[0].name)
        val roll = twice.rolls.first { it.characterId == first.id }
        assertNotEquals("attack-part", roll.subgroups.single().id)
        assertEquals(roll.subgroups.single().id, roll.diceAppearance.subgroupStyleIds.keys.single())
        assertEquals(first.defaultDiceStyleId, roll.diceAppearance.subgroupStyleIds.values.single())
        assertTrue(AppDataValidator.validate(twice).isEmpty())
    }

    @Test fun rejectsMetadataAndUnsupportedVersion() {
        val malicious = packet(sample())
        malicious.getJSONObject("data").put("characterSyncMetadata", JSONArray().put(JSONObject().put("characterId", "fighter")))
        expectFailure { CharacterTransferCodec.decodeJson(malicious.toString()) }
        malicious.put("formatVersion", 99)
        expectFailure { CharacterTransferCodec.decodeJson(malicious.toString()) }
    }

    @Test fun rejectsUnrelatedCharacterData() {
        val json = packet(sample())
        json.getJSONObject("data").getJSONArray("groups").getJSONObject(0).put("characterId", "foreign")
        expectFailure { CharacterTransferCodec.decodeJson(json.toString()) }
    }



    @Test fun outgoingQrIsReadableByQrDecoder() {
        val url = requireNotNull(CharacterTransferCodec.shareUrlOrNull(sample(), "fighter"))
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 700, 700)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val image = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(image)))
        assertEquals(url, decoded.text)
    }

    @Test fun androidOutgoingShareUrlOpensWebWorkshopAndDecodes() {
        val original = sample()
        val url = CharacterTransferCodec.shareUrlOrNull(original, "fighter")
        requireNotNull(url)
        assertTrue(url.startsWith(CharacterTransferCodec.WORKSHOP_URL + "#character=v1."))
        assertTrue(url.length <= CharacterTransferCodec.MAX_SHARE_URL_LENGTH)
        val token = url.substringAfter("#character=")
        assertEquals(original, CharacterTransferCodec.decodeToken(token))
    }

    @Test fun exportExcludesOtherCharactersUnusedStylesAndPrivateData() {
        val first = sample()
        val other = CharacterProfile("other", "Private hero")
        val full = first.copy(
            characters = first.characters + other,
            diceStyles = first.diceStyles + DiceStyle(
                id = "unused-style", characterId = null, name = "Private color",
                primaryColorArgb = -3, secondaryColorArgb = -4,
            ),
            modifiers = first.modifiers + CharacterModifier("hidden", "other", "Secret", 7),
            groups = first.groups + RollGroup("hidden-group", "other", "Private"),
            rolls = first.rolls + RollDefinition("hidden-roll", "other", "Private", "2d6"),
        )
        val json = CharacterTransferCodec.encodeJson(full, "fighter")
        val transferred = CharacterTransferCodec.decodeJson(json)
        assertEquals(listOf("fighter"), transferred.characters.map { it.id })
        assertEquals(listOf("style-1"), transferred.diceStyles.map { it.id })
        assertEquals(listOf("attack"), transferred.rolls.map { it.id })
        assertEquals(listOf("str"), transferred.modifiers.map { it.id })
        assertFalse(json.contains("Private hero"))
        assertFalse(json.contains("Private color"))
        assertFalse(json.contains("hidden-group"))
        assertTrue(transferred.logs.isEmpty())
        assertTrue(transferred.characterSyncMetadata.isEmpty())
    }

    @Test fun exportStripsLocalImagesAndPreservesOriginalCharacter() {
        val modified = sample().let { data ->
            data.copy(characters = listOf(data.characters.single().copy(
                imageUri = "content://private/photos/secret",
                tag = "Travel notes",
            )))
        }
        val raw = CharacterTransferCodec.encodeJson(modified, "fighter")
        assertFalse(raw.contains("content://private/"))
        val decoded = CharacterTransferCodec.decodeJson(raw)
        assertNull(decoded.characters.single().imageUri)
        assertEquals("content://private/photos/secret", modified.characters.single().imageUri)
    }

    @Test fun oversizeUrlOffersFileExportWithoutTruncation() {
        val random = java.util.Random(1234L)
        val text = ByteArray(4096).also(random::nextBytes)
        val opaqueTag = Base64.getUrlEncoder().encodeToString(text)
        val data = sample().let {
            it.copy(characters = listOf(it.characters.single().copy(tag = opaqueTag)))
        }
        val raw = CharacterTransferCodec.encodeJson(data, "fighter")
        assertEquals(data.characters.single().tag, CharacterTransferCodec.decodeJson(raw).characters.single().tag)
        assertNull(CharacterTransferCodec.shareUrlOrNull(data, "fighter"))
    }

    @Test fun exportedDataCanImportAsFreshIndependentCopy() {
        val source = sample()
        val payload = CharacterTransferCodec.decodeJson(CharacterTransferCodec.encodeJson(source, "fighter"))
        val copied = CharacterTransferCodec.importCopy(source, payload)
        assertEquals(2, copied.characters.size)
        val importedId = copied.characters.last().id
        assertNotEquals(source.characters.single().id, importedId)
        assertEquals(source.rolls.single(), copied.rolls.first())
        assertEquals(source.characters.single().defaultDiceStyleId, copied.characters.first().defaultDiceStyleId)
        assertTrue(AppDataValidator.validate(copied).isEmpty())
    }

    @Test fun recipientPreferencesAreExplicitValidatedAndCopyIndependent() {
        val inbound = sample()
        val adjusted = CharacterTransferCodec.withRecipientPreferences(
            inbound, "  New fighter  ", 12,
        )
        assertEquals("New fighter", adjusted.characters.single().name)
        assertEquals(12, adjusted.characters.single().level)
        assertEquals("Aric", inbound.characters.single().name)
        assertEquals(1, inbound.characters.single().level)
        assertEquals(inbound.rolls, adjusted.rolls)
        val merged = CharacterTransferCodec.importCopy(inbound, adjusted)
        assertEquals(2, merged.characters.size)
        assertEquals(12, merged.characters.last().level)
        assertEquals("New fighter", merged.characters.last().name)
        assertNotEquals(inbound.characters.single().id, merged.characters.last().id)
        assertTrue(AppDataValidator.validate(merged).isEmpty())
    }

    @Test fun recipientPreferencesRejectInvalidLevelsAndNames() {
        val inbound = sample()
        listOf("", " ", "A".repeat(161)).forEach { name ->
            expectFailure { CharacterTransferCodec.withRecipientPreferences(inbound, name, 2) }
        }
        listOf(0, -1, 10_000).forEach { level ->
            expectFailure { CharacterTransferCodec.withRecipientPreferences(inbound, "New", level) }
        }
        assertEquals(inbound, sample())
    }


    @Test fun cappedPeriodicRulesSurvivePortableJsonAndLegacyDefaults() {
        val source=sample().copy(
            rolls=sample().rolls.map { roll -> roll.copy(
                levelRules=listOf(RollLevelRule(
                    id="repeat",kind=LevelRuleKind.EVERY_LEVELS,trigger=2,
                    expression="1d4+1",startLevel=3,maxRepeats=8,
                )),
            ) },
        )
        val json=CharacterTransferCodec.encodeJson(source,"fighter")
        assertEquals(2,JSONObject(json).getInt("formatVersion"))
        val decoded=CharacterTransferCodec.decodeJson(json)
        assertEquals(3,decoded.rolls.single().levelRules.single().startLevel)
        assertEquals(8,decoded.rolls.single().levelRules.single().maxRepeats)
        assertEquals(source,decoded)
        assertTrue(AppDataValidator.validate(decoded).isEmpty())
        val copy=CharacterTransferCodec.importCopy(AppData(), decoded)
        assertTrue(AppDataValidator.validate(copy).isEmpty())
        assertEquals(8,copy.rolls.single().levelRules.single().maxRepeats)

        val legacy=packet(source)
        val legacyRules=legacy.getJSONObject("data").getJSONArray("rolls")
            .getJSONObject(0).getJSONArray("levelRules").getJSONObject(0)
        legacyRules.remove("startLevel")
        legacyRules.remove("maxRepeats")
        val older=CharacterTransferCodec.decodeJson(legacy.toString())
        assertEquals(1,legacy.getInt("formatVersion"))
        assertEquals(null,older.rolls.single().levelRules.single().startLevel)
        assertEquals(null,older.rolls.single().levelRules.single().maxRepeats)
        assertTrue(AppDataValidator.validate(older).isEmpty())
    }

    @Test fun periodicRuleWithInvalidCapIsRejectedAtNativeImportBoundary() {
        val json=packet(sample())
        val rules=json.getJSONObject("data").getJSONArray("rolls")
            .getJSONObject(0).getJSONArray("levelRules")
        rules.put(JSONObject()
            .put("id","malicious")
            .put("kind","EVERY_LEVELS")
            .put("trigger",2)
            .put("expression","1d4")
            .put("startLevel",3)
            .put("maxRepeats",41))
        expectFailure { CharacterTransferCodec.decodeJson(json.toString()) }
    }

    private fun expectFailure(action: () -> Unit) {
        try { action(); fail("Expected transfer validation failure") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }
}
