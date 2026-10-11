package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollDefinition
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

    private fun expectFailure(action: () -> Unit) {
        try { action(); fail("Expected transfer validation failure") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }
}
