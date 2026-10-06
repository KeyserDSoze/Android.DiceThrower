package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSyncMetadataTest {
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
    }
}
