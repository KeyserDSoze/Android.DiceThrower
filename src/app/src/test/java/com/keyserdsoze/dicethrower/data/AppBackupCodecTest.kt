package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupCodecTest {
    @Test
    fun backupRoundTripPreservesDataSettingsAndLanguage() {
        val data = sampleData()
        val settings = AppSettings(
            themeMode = ThemeMode.DARK,
            shakeEnabled = false,
            animationsEnabled = true,
            showRollButton = true,
            rollButtonPosition = RollButtonPosition.TOP_CENTER,
            logRetention = 100,
        )

        val raw = AppBackupCodec.encode(
            data = data,
            settings = settings,
            language = "it",
            exportedAt = 123456789L,
        )
        val decoded = AppBackupCodec.decode(raw)

        assertEquals(data, decoded.data)
        assertEquals(settings, decoded.settings)
        assertEquals("it", decoded.language)
        assertEquals(123456789L, decoded.exportedAt)
        assertTrue(raw.contains("\"imageMode\": \"uri-reference\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownBackupFormat() {
        AppBackupCodec.decode(
            JSONObject()
                .put("format", "something-else")
                .put("formatVersion", 1)
                .toString(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedBackupVersion() {
        AppBackupCodec.decode(
            JSONObject()
                .put("format", AppBackupCodec.FORMAT)
                .put("formatVersion", 99)
                .toString(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBrokenGroupReferences() {
        val data = sampleData().copy(
            rolls = sampleData().rolls.map { it.copy(groupId = "missing-group") },
        )

        AppBackupCodec.encode(
            data = data,
            settings = AppSettings(),
            language = "en",
            exportedAt = 1L,
        )
    }

    @Test
    fun dataCodecReadsVersionOneShapeWithSafeDefaults() {
        val json = JSONObject()
            .put(
                "characters",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("id", "old-character")
                        .put("name", "Legacy")
                        .put("tag", "Old Game")
                        .put("order", 0),
                ),
            )
            .put("groups", org.json.JSONArray())
            .put("rolls", org.json.JSONArray())
            .put("logs", org.json.JSONArray())

        val decoded = AppDataJsonCodec.decodeData(json)

        assertEquals(1, decoded.characters.single().level)
        assertTrue(decoded.modifiers.isEmpty())
    }

    private fun sampleData(): AppData = AppData(
        characters = listOf(
            CharacterProfile(
                id = "character",
                name = "Alyndra",
                imageUri = "content://example/portrait",
                tag = "Arcane",
                level = 8,
                order = 0,
            ),
        ),
        modifiers = listOf(
            CharacterModifier(
                id = "modifier",
                characterId = "character",
                name = "Intelligence",
                value = 4,
                order = 0,
            ),
        ),
        groups = listOf(
            RollGroup(
                id = "group",
                characterId = "character",
                name = "Attack Spells",
                order = 0,
            ),
        ),
        rolls = listOf(
            RollDefinition(
                id = "roll",
                characterId = "character",
                name = "Fireball",
                expression = "6d6+{Intelligence}",
                groupId = "group",
                order = 0,
                levelRules = listOf(
                    RollLevelRule(
                        id = "rule",
                        kind = LevelRuleKind.FROM_LEVEL,
                        trigger = 10,
                        expression = "1d6",
                    ),
                ),
            ),
        ),
        logs = listOf(
            RollLog(
                id = "log",
                characterId = "character",
                rollDefinitionId = "roll",
                rollName = "Fireball",
                expression = "6d6+4",
                total = 25,
                detail = "6d6[3,4,5,2,3,4] +4",
                timestamp = 42L,
            ),
        ),
    )
}
