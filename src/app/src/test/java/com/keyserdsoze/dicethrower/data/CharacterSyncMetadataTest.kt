package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.data.cloud.CloudDocumentCodec
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.CharacterSyncMetadata
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollLogPart
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSyncMetadataTest {
    @Test
    fun multipartLogPartsParticipateInRevisionWithoutChangingLegacyLogs() {
        val log = RollLog(
            id = "sample-log", characterId = CHARACTER_ID, rollDefinitionId = "roll",
            rollName = "Test", expression = "(1d6)+(1d20)",
            total = 24, detail = "legacy", timestamp = 11L,
        )
        val legacy = AppData(
            characters = listOf(CharacterProfile(id = CHARACTER_ID, name = "Test")),
            logs = listOf(log),
        )
        val multi = legacy.copy(logs = listOf(log.copy(parts = listOf(
            RollLogPart("Damage", "1d6", 4, "1d6[4]"),
            RollLogPart("Hit", "1d20", 20, "1d20[20]"),
        ))))
        assertNotEquals(CharacterRevision.revision(legacy, CHARACTER_ID), CharacterRevision.revision(multi, CHARACTER_ID))
        assertTrue(CharacterRevision.canonicalContent(multi, CHARACTER_ID).contains("rollLogPart"))
    }

    @Test
    fun canonicalRevisionIsStableAcrossCollectionOrdering() {
        val first = sampleData()
        val reordered = first.copy(modifiers = first.modifiers.reversed())

        assertEquals(
            CharacterRevision.canonicalContent(first, CHARACTER_ID),
            CharacterRevision.canonicalContent(reordered, CHARACTER_ID),
        )
        assertEquals(
            CharacterRevision.revision(first, CHARACTER_ID),
            CharacterRevision.revision(reordered, CHARACTER_ID),
        )
    }

    @Test
    fun graphEditChangesRevisionButMetadataDoesNotParticipateInHash() {
        val initial = SyncMetadataManager.ensureMetadata(sampleData(), "writer-a", 100L)
        val renamed = initial.copy(
            characters = initial.characters.map { it.copy(name = "Alyndra the Wise") },
        )

        assertEquals(
            initial.characterSyncMetadata.single().revision,
            CharacterRevision.revision(initial, CHARACTER_ID),
        )
        assertNotEquals(
            CharacterRevision.revision(initial, CHARACTER_ID),
            CharacterRevision.revision(renamed, CHARACTER_ID),
        )
    }

    @Test
    fun changingCharacterTableChangesRevision() {
        val initial = sampleData()
        val changed = initial.copy(
            characters = initial.characters.map { it.copy(diceTableTheme = DiceTableTheme.EMERALD) },
        )

        assertNotEquals(
            CharacterRevision.revision(initial, CHARACTER_ID),
            CharacterRevision.revision(changed, CHARACTER_ID),
        )
    }

    @Test
    fun changingCharacterTableImageChangesRevision() {
        val initial = sampleData()
        val image = CharacterImageAssets.createRef("table".toByteArray(), "image/jpeg")
        val changed = initial.copy(
            characters = initial.characters.map { it.copy(diceTableImage = image) },
        )

        assertNotEquals(
            CharacterRevision.revision(initial, CHARACTER_ID),
            CharacterRevision.revision(changed, CHARACTER_ID),
        )
    }

    @Test
    fun changingRollSubgroupsChangesCanonicalRevision() {
        val grouped = sampleData().copy(
            rolls = listOf(
                RollDefinition(
                    id = "combo",
                    characterId = CHARACTER_ID,
                    name = "Combo",
                    expression = "(1d20+2)-(1d4)",
                    subgroups = listOf(
                        RollSubgroup("attack", "Attack", "1d20+2"),
                        RollSubgroup("penalty", "Penalty", "1d4", RollSubgroupOperator.SUBTRACT),
                    ),
                ),
            ),
        )
        val changed = grouped.copy(
            rolls = grouped.rolls.map { roll ->
                roll.copy(
                    expression = "(1d20+2)-(2d4)",
                    subgroups = roll.subgroups.map { subgroup ->
                        if (subgroup.id == "penalty") subgroup.copy(expression = "2d4") else subgroup
                    },
                )
            },
        )

        assertNotEquals(
            CharacterRevision.revision(grouped, CHARACTER_ID),
            CharacterRevision.revision(changed, CHARACTER_ID),
        )
        assertTrue(CharacterRevision.canonicalContent(grouped, CHARACTER_ID).contains("rollSubgroup"))
    }

    @Test
    fun changingSubgroupAppearanceChangesCanonicalRevision() {
        val base = sampleData().copy(
            rolls = listOf(
                RollDefinition(
                    id = "combo",
                    characterId = CHARACTER_ID,
                    name = "Combo",
                    expression = "(1d20)+(2d6)",
                    subgroups = listOf(
                        RollSubgroup("attack", "Attack", "1d20"),
                        RollSubgroup("damage", "Damage", "2d6"),
                    ),
                ),
            ),
        )
        val changed = base.copy(
            rolls = base.rolls.map { roll ->
                roll.copy(
                    diceAppearance = RollDiceAppearance(
                        mode = DiceAppearanceMode.PER_DIE,
                        subgroupStyleIds = mapOf("damage" to "style"),
                    ),
                )
            },
        )

        assertNotEquals(
            CharacterRevision.revision(base, CHARACTER_ID),
            CharacterRevision.revision(changed, CHARACTER_ID),
        )
    }

    @Test
    fun defaultArcaneTableKeepsVersionFiveCanonicalRevision() {
        val legacyCharacterOnly = AppData(
            characters = listOf(CharacterProfile(id = CHARACTER_ID, name = "Alyndra")),
        )

        assertEquals(
            "afa7f3bff7c22213e5bc6cfaa8ed142f13a8dc1de74f0630533769e2a94bec1b",
            CharacterRevision.revision(legacyCharacterOnly, CHARACTER_ID),
        )
        assertTrue(
            !CharacterRevision.canonicalContent(legacyCharacterOnly, CHARACTER_ID)
                .contains("diceTableTheme"),
        )
    }

    @Test
    fun unchangedContentKeepsStableMetadata() {
        val initial = SyncMetadataManager.ensureMetadata(sampleData(), "writer-a", 100L)
        val unchanged = SyncMetadataManager.reconcileLocalEdit(
            previous = initial,
            proposed = initial,
            writerId = "writer-b",
            now = 500L,
        )

        assertEquals(initial.characterSyncMetadata, unchanged.characterSyncMetadata)
    }

    @Test
    fun localEditUpdatesWriterAndMonotonicTimestampWhileKeepingSyncBase() {
        val initial = SyncMetadataManager.ensureMetadata(sampleData(), "writer-a", 100L)
        val base = initial.characterSyncMetadata.single().revision
        val synced = SyncMetadataManager.markSynced(initial, CHARACTER_ID, base)
        val editedContent = synced.copy(
            modifiers = synced.modifiers.map { modifier ->
                if (modifier.id == "modifier-a") modifier.copy(value = 9) else modifier
            },
        )

        val edited = SyncMetadataManager.reconcileLocalEdit(
            previous = synced,
            proposed = editedContent,
            writerId = "writer-b",
            now = 50L,
        )
        val metadata = edited.characterSyncMetadata.single()

        assertEquals("writer-b", metadata.writerId)
        assertEquals(101L, metadata.updatedAt)
        assertEquals(base, metadata.baseRevision)
        assertNotEquals(base, metadata.revision)
        assertTrue(AppDataValidator.validate(edited).isEmpty())
    }

    @Test
    fun levelRuleBoundsParticipateInRevisionWithoutChangingLegacyHashes() {
        // Pinned before bounds took part in the revision: unbounded rules keep their hash.
        assertEquals(LEGACY_WIZARD_REVISION, CharacterRevision.revision(wizard(), WIZARD_ID))

        val bounded = CharacterRevision.revision(wizard(startLevel = 3, maxRepeats = 8), WIZARD_ID)
        assertNotEquals(LEGACY_WIZARD_REVISION, bounded)
        assertNotEquals(bounded, CharacterRevision.revision(wizard(startLevel = 3, maxRepeats = 9), WIZARD_ID))
        assertNotEquals(bounded, CharacterRevision.revision(wizard(startLevel = 4, maxRepeats = 8), WIZARD_ID))
        assertNotEquals(bounded, CharacterRevision.revision(wizard(startLevel = null, maxRepeats = 8), WIZARD_ID))
    }

    @Test
    fun boundedCharacterStampedBeforeBoundsCountedStaysValidUntilEdited() {
        // Saved, or uploaded to Drive, by a version that ignored bounds: same hash as unbounded.
        val legacy = CharacterSyncMetadata(WIZARD_ID, 10L, LEGACY_WIZARD_REVISION, "old-device", LEGACY_WIZARD_REVISION)
        val stored = wizard(startLevel = 3, maxRepeats = 8).copy(characterSyncMetadata = listOf(legacy))

        assertTrue(AppDataValidator.validate(stored).isEmpty())
        assertEquals(stored, decodeStoredData(AppDataJsonCodec.encodeData(stored).toString()))
        val document = CloudDocumentCodec.buildCharacterDocument(stored, WIZARD_ID)
        assertEquals(document, CloudDocumentCodec.decodeCharacter(CloudDocumentCodec.encodeCharacter(document)))
        // Loading must not re-stamp it: that would look like a local edit and upload it again.
        assertEquals(stored, SyncMetadataManager.ensureMetadata(stored, "new-device", 50L))

        // Changing only a bound is a real edit that syncs.
        val edited = SyncMetadataManager.reconcileLocalEdit(stored, wizard(startLevel = 3, maxRepeats = 9), "new-device", 60L)
        val metadata = edited.characterSyncMetadata.single()
        assertEquals(CharacterRevision.revision(edited, WIZARD_ID), metadata.revision)
        assertEquals("new-device", metadata.writerId)
        assertEquals(60L, metadata.updatedAt)
        assertEquals(LEGACY_WIZARD_REVISION, metadata.baseRevision)
        assertTrue(AppDataValidator.validate(edited).isEmpty())

        // The older hash never covers content that changed in any other way.
        val renamed = stored.copy(characters = stored.characters.map { it.copy(name = "Archmage") })
        assertFalse(AppDataValidator.validate(renamed).isEmpty())
    }

    private fun wizard(startLevel: Int? = null, maxRepeats: Int? = null) = AppData(
        characters = listOf(CharacterProfile(id = WIZARD_ID, name = "Wizard", level = 9)),
        rolls = listOf(RollDefinition(
            id = "missile", characterId = WIZARD_ID, name = "Magic Missile", expression = "3d4+3",
            levelRules = listOf(RollLevelRule(
                "dart", LevelRuleKind.EVERY_LEVELS, trigger = 2, expression = "1d4+1",
                startLevel = startLevel, maxRepeats = maxRepeats,
            )),
        )),
    )

    @Test
    fun classifiesSameLocalOnlyRemoteOnlyAndConcurrentChanges() {
        val base = "base"
        val local = "local"
        val remote = "remote"

        assertEquals(SyncChangeState.SAME, SyncMetadataManager.classify(local, local, base))
        assertEquals(SyncChangeState.LOCAL_ONLY, SyncMetadataManager.classify(local, base, base))
        assertEquals(SyncChangeState.REMOTE_ONLY, SyncMetadataManager.classify(base, remote, base))
        assertEquals(SyncChangeState.CONFLICT, SyncMetadataManager.classify(local, remote, base))
        assertEquals(SyncChangeState.CONFLICT, SyncMetadataManager.classify(local, remote, null))
    }

    private fun sampleData(): AppData = AppData(
        characters = listOf(CharacterProfile(id = CHARACTER_ID, name = "Alyndra")),
        modifiers = listOf(
            CharacterModifier(
                id = "modifier-b",
                characterId = CHARACTER_ID,
                name = "Strength",
                value = 2,
                order = 1,
            ),
            CharacterModifier(
                id = "modifier-a",
                characterId = CHARACTER_ID,
                name = "Intelligence",
                value = 4,
                order = 0,
            ),
        ),
    )

    private companion object {
        const val CHARACTER_ID = "character"
        const val WIZARD_ID = "wizard"
        const val LEGACY_WIZARD_REVISION = "558b60e01cca4dbae6e19e0c3f36960cdaefe04873dc9ff1798fc395bbbd49ae"
    }
}
