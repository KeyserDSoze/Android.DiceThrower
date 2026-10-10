package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings
import com.keyserdsoze.dicethrower.model.RollVisualProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollVisualSettingsPersistenceTest {
    private val roll = RollDefinition(
        id = "roll", characterId = "hero", name = "Arcane strike", expression = "(1d20)",
    )
    private val data = AppData(
        characters = listOf(CharacterProfile("hero", "Hero")),
        rolls = listOf(roll),
    )

    @Test
    fun globalVisualSettingsRoundTripThroughSettingsAndBackupWithSafeLegacyDefault() {
        val selected = RollVisualEffectsSettings.preset(RollVisualProfile.SUBTLE).custom {
            copy(badge = false, groupLanes = false, winnerSpotlight = true,
                tableAura = true, actionCues = false)
        }
        val settings = AppSettings(visualEffects = selected)
        assertEquals(selected, AppDataJsonCodec.decodeSettings(
            AppDataJsonCodec.encodeSettings(settings)).visualEffects)
        val backup = AppBackupCodec.decode(AppBackupCodec.encode(data, settings, "it"))
        assertEquals(settings, backup.settings)
        assertEquals(data, backup.data)

        val oldSettings = AppDataJsonCodec.encodeSettings(settings)
        oldSettings.remove("visualEffects")
        assertEquals(RollVisualEffectsSettings(),
            AppDataJsonCodec.decodeSettings(oldSettings).visualEffects)

        // Per-Roll legacy fields still decode, but cannot override the global choice.
        val legacyRoll = roll.copy(visualEffects = RollVisualEffectsSettings.preset(RollVisualProfile.OFF))
        val global = settings.copy(visualEffects = RollVisualEffectsSettings.preset(RollVisualProfile.BALANCED))
        assertEquals(RollVisualProfile.BALANCED, global.effectiveVisualEffects().profile)
        assertEquals(RollVisualProfile.OFF, legacyRoll.visualEffects.profile)
        assertEquals(global.effectiveVisualEffects(),
            global.copy().effectiveVisualEffects())
    }

    @Test
    fun reducedMotionDisablesAnimatedAccentsWithoutDiscardingGlobalSelection() {
        val preferred = RollVisualEffectsSettings.preset(RollVisualProfile.BALANCED)
        val settings = AppSettings(animationsEnabled = false, visualEffects = preferred)
        val effective = settings.effectiveVisualEffects()
        assertFalse(effective.tableAura || effective.particles || effective.actionCues ||
            effective.resultTransitions || effective.cameraImpact)
        assertTrue(effective.badge && effective.groupLanes && effective.winnerSpotlight)
        assertEquals(preferred, settings.visualEffects)
        assertEquals(preferred, settings.copy(animationsEnabled = true).effectiveVisualEffects())
    }

    @Test
    fun balancedProfileIsDefaultAndOldDataNeverLoseEffectsOnUpgrade() {
        val json = AppDataJsonCodec.encodeData(data)
        assertEquals(15, json.getInt("version"))
        json.getJSONArray("rolls").getJSONObject(0).remove("visualEffects")
        json.put("version", 14)
        val decoded = AppDataJsonCodec.decodeData(json)
        assertEquals(data, decoded)
        assertEquals(RollVisualProfile.BALANCED, decoded.rolls.single().visualEffects.profile)
        assertEquals(CharacterRevision.revision(data, "hero"),
            CharacterRevision.revision(decoded, "hero"))
    }

    @Test
    fun presetsAndEveryToggleSurviveBackupSyncAndCopy() {
        val allOff = RollVisualEffectsSettings.preset(RollVisualProfile.OFF)
        assertTrue(listOf(allOff.badge, allOff.groupLanes, allOff.winnerSpotlight,
            allOff.tableAura, allOff.particles, allOff.actionCues,
            allOff.resultTransitions, allOff.cameraImpact).none { it })
        val subtle = RollVisualEffectsSettings.preset(RollVisualProfile.SUBTLE)
        assertTrue(subtle.badge && subtle.groupLanes && subtle.winnerSpotlight)
        assertFalse(subtle.particles || subtle.tableAura || subtle.resultTransitions || subtle.cameraImpact)
        val custom = subtle.custom { copy(badge = false, actionCues = false,
            tableAura = true, particles = true, resultTransitions = true) }
        assertEquals(RollVisualProfile.CUSTOM, custom.profile)
        val changed = data.copy(rolls = listOf(roll.copy(visualEffects = custom)))

        assertTrue(AppDataValidator.validate(changed).isEmpty())
        assertNotEquals(CharacterRevision.revision(data, "hero"),
            CharacterRevision.revision(changed, "hero"))
        assertEquals(changed, AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeData(changed)))
        assertEquals(changed, AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeDataForSync(changed)))
        assertEquals(changed,
            AppBackupCodec.decode(AppBackupCodec.encode(changed, AppSettings(), "it")).data)

        var nextId = 0
        val clone = CharacterDataOperations.duplicateCharacter(changed, "hero", "Cloned",
            idFactory = { "copy-${nextId++}" })
        assertTrue(AppDataValidator.validate(clone).isEmpty())
        assertEquals(custom, clone.rolls.single { it.characterId != "hero" }.visualEffects)
    }

    @Test
    fun globalAnimationsOffPreservesPreferencesButDisablesAnimatedAccents() {
        val custom = RollVisualEffectsSettings.preset(RollVisualProfile.BALANCED)
        val reduced = custom.withGlobalMotionEnabled(false)
        assertFalse(reduced.cameraImpact || reduced.tableAura || reduced.particles ||
            reduced.actionCues || reduced.resultTransitions)
        assertTrue(reduced.groupLanes && reduced.winnerSpotlight && reduced.badge)
        assertEquals(custom, custom.withGlobalMotionEnabled(true))
    }
}
