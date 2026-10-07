package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.CharacterSyncMetadata
import com.keyserdsoze.dicethrower.model.ConflictPolicy
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import com.keyserdsoze.dicethrower.model.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

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
            conflictPolicy = ConflictPolicy.LATEST_WINS,
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
        assertTrue(raw.contains("\"imageMode\": \"portable-assets\""))
        assertTrue(raw.contains("\"diceStyles\""))
        assertTrue(raw.contains("\"characterSyncMetadata\""))
    }

    @Test
    fun premiumTableThemeRoundTripsThroughBackup() {
        val data = AppData(
            characters = listOf(
                CharacterProfile(
                    id = "premium-table-hero",
                    name = "Hero",
                    diceTableTheme = DiceTableTheme.ASTRAL_VOID,
                ),
            ),
        )

        val decoded = AppBackupCodec.decode(
            AppBackupCodec.encode(
                data = data,
                settings = AppSettings(),
                language = "en",
                exportedAt = 42L,
            ),
        )

        assertEquals(DiceTableTheme.ASTRAL_VOID, decoded.data.characters.single().diceTableTheme)
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
    fun dataCodecReadsVersionOneShapeWithSafeDiceDefaults() {
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
            .put(
                "rolls",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("id", "old-roll")
                        .put("characterId", "old-character")
                        .put("name", "Legacy roll")
                        .put("expression", "1d20")
                        .put("order", 0),
                ),
            )
            .put("logs", org.json.JSONArray())

        val decoded = AppDataJsonCodec.decodeData(json)

        assertEquals(1, decoded.characters.single().level)
        assertNull(decoded.characters.single().defaultDiceStyleId)
        assertEquals(DiceTableTheme.ARCANE, decoded.characters.single().diceTableTheme)
        assertNull(decoded.characters.single().diceTableImage)
        assertTrue(decoded.modifiers.isEmpty())
        assertTrue(decoded.diceStyles.isEmpty())
        assertTrue(decoded.characterSyncMetadata.isEmpty())
        assertEquals(DiceAppearanceMode.CHARACTER_DEFAULT, decoded.rolls.single().diceAppearance.mode)
        assertTrue(decoded.rolls.single().subgroups.isEmpty())

        val migrated = SyncMetadataManager.ensureMetadata(decoded, "local-writer", 500L)
        assertEquals("local-writer", migrated.characterSyncMetadata.single().writerId)
        assertEquals(500L, migrated.characterSyncMetadata.single().updatedAt)
        assertTrue(AppDataValidator.validate(migrated).isEmpty())
    }

    @Test
    fun groupedRollsRoundTripThroughDataCodec() {
        val data = AppData(
            characters = listOf(CharacterProfile(id = "hero", name = "Hero", level = 5)),
            rolls = listOf(
                RollDefinition(
                    id = "combo",
                    characterId = "hero",
                    name = "Attack and damage",
                    expression = "(1d20+{level})-(1d4)",
                    subgroups = listOf(
                        RollSubgroup("attack", "Attack", "1d20+{level}"),
                        RollSubgroup("penalty", "Penalty", "1d4", RollSubgroupOperator.SUBTRACT),
                    ),
                ),
            ),
        )

        val decoded = AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeData(data))

        assertEquals(data, decoded)
        assertTrue(AppDataValidator.validate(decoded).isEmpty())
    }

    @Test
    fun groupedRollsRoundTripThroughPortableBackup() {
        val data = AppData(
            characters = listOf(CharacterProfile(id = "hero", name = "Hero", level = 5)),
            diceStyles = listOf(
                DiceStyle(
                    id = "damage-style",
                    characterId = "hero",
                    name = "Damage",
                    material = DiceMaterial.METAL,
                    primaryColorArgb = 0xFFAA0000.toInt(),
                    secondaryColorArgb = 0xFFFFFFFF.toInt(),
                ),
            ),
            rolls = listOf(
                RollDefinition(
                    id = "combo",
                    characterId = "hero",
                    name = "Attack and damage",
                    expression = "(1d20+{level})+(2d6*2)",
                    subgroups = listOf(
                        RollSubgroup("attack", "Attack", "1d20+{level}"),
                        RollSubgroup("damage", "Damage", "2d6*2"),
                    ),
                    diceAppearance = RollDiceAppearance(
                        mode = DiceAppearanceMode.PER_DIE,
                        subgroupStyleIds = mapOf("damage" to "damage-style"),
                    ),
                ),
            ),
        )

        val backup = AppBackupCodec.encode(
            data = data,
            settings = AppSettings(),
            language = "en",
            exportedAt = 123L,
        )
        val decoded = AppBackupCodec.decode(backup)

        assertEquals(data, decoded.data)
        assertEquals(data.rolls.single().subgroups, decoded.data.rolls.single().subgroups)
        assertEquals(
            mapOf("damage" to "damage-style"),
            decoded.data.rolls.single().diceAppearance.subgroupStyleIds,
        )
    }

    @Test
    fun portableImageRoundTripRestoresValidatedBytes() {
        val bytes = "portable portrait".toByteArray()
        val tableBytes = "vertical table photo".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/png")
        val tableRef = CharacterImageAssets.createRef(tableBytes, "image/jpeg")
        val data = SyncMetadataManager.ensureMetadata(
            AppData(
                characters = listOf(
                    CharacterProfile(
                        id = "portable-character",
                        name = "Portable",
                        image = ref,
                        diceTableImage = tableRef,
                    ),
                ),
            ),
            writerId = "writer",
            now = 100L,
        )
        val raw = AppBackupCodec.encode(
            data = data,
            settings = AppSettings(),
            language = "en",
            exportedAt = 200L,
            imageAssets = listOf(
                PortableCharacterImageAsset(ref, bytes),
                PortableCharacterImageAsset(tableRef, tableBytes),
            ),
        )

        val decoded = AppBackupCodec.decode(raw)

        assertEquals(data, decoded.data)
        assertEquals(ref, decoded.data.characters.single().image)
        assertEquals(tableRef, decoded.data.characters.single().diceTableImage)
        assertTrue(decoded.imageAssets.first { it.ref == ref }.bytes.contentEquals(bytes))
        assertTrue(decoded.imageAssets.first { it.ref == tableRef }.bytes.contentEquals(tableBytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRejectsMissingPortableImageAsset() {
        val bytes = "portrait".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/webp")
        val data = SyncMetadataManager.ensureMetadata(
            AppData(characters = listOf(CharacterProfile(id = "character", name = "Hero", image = ref))),
            "writer",
            1L,
        )

        AppBackupCodec.encode(data, AppSettings(), "en", imageAssets = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRejectsCorruptPortableImageBytes() {
        val bytes = "portrait".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/jpeg")
        val data = SyncMetadataManager.ensureMetadata(
            AppData(characters = listOf(CharacterProfile(id = "character", name = "Hero", image = ref))),
            "writer",
            1L,
        )
        val root = JSONObject(
            AppBackupCodec.encode(
                data,
                AppSettings(),
                "en",
                imageAssets = listOf(PortableCharacterImageAsset(ref, bytes)),
            ),
        )
        root.getJSONArray("imageAssets").getJSONObject(0)
            .put("base64", Base64.getEncoder().encodeToString("tampered".toByteArray()))

        AppBackupCodec.decode(root.toString())
    }

    @Test
    fun versionOneBackupStillReadsLegacyImageUri() {
        val legacyData = JSONObject()
            .put("characters", org.json.JSONArray().put(
                JSONObject()
                    .put("id", "legacy")
                    .put("name", "Legacy")
                    .put("imageUri", "content://legacy/portrait"),
            ))
            .put("groups", org.json.JSONArray())
            .put("rolls", org.json.JSONArray())
            .put("logs", org.json.JSONArray())
            .put("characterSyncMetadata", org.json.JSONArray().put(
                JSONObject()
                    .put("characterId", "legacy")
                    .put("updatedAt", 50L)
                    .put("revision", "a".repeat(64))
                    .put("writerId", "old-writer"),
            ))
        val raw = JSONObject()
            .put("format", AppBackupCodec.FORMAT)
            .put("formatVersion", 1)
            .put("data", legacyData)
            .put("settings", JSONObject())
            .toString()

        val decoded = AppBackupCodec.decode(raw)

        assertEquals("content://legacy/portrait", decoded.data.characters.single().imageUri)
        assertNull(decoded.data.characters.single().image)
        assertTrue(decoded.data.characterSyncMetadata.isEmpty())
        assertTrue(decoded.imageAssets.isEmpty())
    }

    private fun sampleData(): AppData {
        val data = AppData(
        characters = listOf(
            CharacterProfile(
                id = "character",
                name = "Alyndra",
                tag = "Arcane",
                level = 8,
                order = 0,
                defaultDiceStyleId = "style-blue",
                diceTableTheme = DiceTableTheme.EMERALD,
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
                diceAppearance = RollDiceAppearance(
                    mode = DiceAppearanceMode.PER_DIE,
                    perDieStyleIds = mapOf("0:0" to "style-steel"),
                    randomStyleIds = listOf("style-blue", "style-steel"),
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
        diceStyles = listOf(
            DiceStyle(
                id = "style-blue",
                characterId = "character",
                name = "Arcane Blue",
                material = DiceMaterial.GLOSSY_RESIN,
                primaryColorArgb = 0xFF2563EB.toInt(),
                secondaryColorArgb = 0xFFF6C453.toInt(),
                order = 0,
            ),
            DiceStyle(
                id = "style-steel",
                characterId = "character",
                name = "Steel",
                material = DiceMaterial.METAL,
                primaryColorArgb = 0xFF64748B.toInt(),
                secondaryColorArgb = 0xFFE2E8F0.toInt(),
                order = 1,
            ),
        ),
        )
        val revision = CharacterRevision.revision(data, "character")
        return data.copy(
            characterSyncMetadata = listOf(
                CharacterSyncMetadata(
                    characterId = "character",
                    updatedAt = 1234L,
                    revision = revision,
                    writerId = "writer-a",
                    baseRevision = revision,
                ),
            ),
        )
    }
}
