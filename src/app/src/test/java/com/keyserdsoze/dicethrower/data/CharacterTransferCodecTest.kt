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

    private fun expectFailure(action: () -> Unit) {
        try { action(); fail("Expected transfer validation failure") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }
}
