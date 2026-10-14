package com.keyserdsoze.dicethrower

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keyserdsoze.dicethrower.data.CharacterTransferCodec
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Actual app-side handoff from a browser's dicethrower:// URL, independent of
 * the system camera/browser on the emulator. Real camera -> Pages -> app stays
 * a separate physical-device acceptance test (#165).
 */
@RunWith(AndroidJUnit4::class)
class CharacterDeepLinkImportTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun validViewIntentShowsPreviewAndImportsAnIndependentCopyOnlyAfterConfirmation() {
        val name = "Shared Mage " + UUID.randomUUID().toString().take(8)
        val characterId = "shared"
        val shared = AppData(
            characters = listOf(CharacterProfile(characterId, name, level = 7)),
            groups = listOf(RollGroup("spells", characterId, "Spell Lv 3")),
            rolls = listOf(RollDefinition(
                id = "fireball", characterId = characterId, groupId = "spells",
                name = "Fireball", expression = "8d6", minimumLevel = 5,
            )),
        )
        val token = CharacterTransferCodec.encodeToken(shared, characterId)
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("dicethrower://import?data=" + Uri.encode(token)))
        // This test uses the same implicit VIEW+BROWSABLE URI that the web
        // workshop button invokes; check Android's intent-filter registration.
        val browserIntent = Intent(Intent.ACTION_VIEW,
            Uri.parse("dicethrower://import?data=" + Uri.encode(token)))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(rule.activity.packageName)
        assertEquals(MainActivity::class.java.name,
            browserIntent.resolveActivity(rule.activity.packageManager)?.className)
        val store = LocalStore(rule.activity)
        val before = store.loadData()
        assertTrue(before.characters.none { it.name == name })

        // Simulate a browser link opening the app from a cold Activity
        // lifecycle. Recreate the managed Activity instead of opening a second
        // task (which prevents ActivityScenarioRule from tearing down cleanly).
        rule.activityRule.scenario.onActivity { it.setIntent(intent) }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText(rule.activity.getString(R.string.import_character_title))
            .assertIsDisplayed()
        // Opening a link alone must not alter local storage.
        assertEquals(before.characters.size, store.loadData().characters.size)

        rule.onNodeWithText(rule.activity.getString(R.string.import_character_confirm))
            .performClick()
        rule.waitForIdle()

        val saved = store.loadData()
        val imported = saved.characters.single { it.name == name }
        assertEquals(7, imported.level)
        assertNotEquals(characterId, imported.id)
        assertTrue(saved.characters.any { it.id == imported.id })
        val group = saved.groups.single { it.characterId == imported.id && it.name == "Spell Lv 3" }
        val roll = saved.rolls.single { it.characterId == imported.id && it.name == "Fireball" }
        assertEquals(group.id, roll.groupId)
        assertEquals("8d6", roll.expression)
        assertEquals(5, roll.minimumLevel)
    }

    @Test
    fun malformedDeepLinkDisplaysErrorWithoutWritingAnyCharacter() {
        val store = LocalStore(rule.activity)
        val before = store.loadData()
        val bad = Intent(Intent.ACTION_VIEW,
            Uri.parse("dicethrower://import?data=not-a-valid-token"))
        rule.activityRule.scenario.onActivity { it.setIntent(bad) }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText(rule.activity.getString(R.string.import_character_error_title))
            .assertIsDisplayed()
        assertEquals(before.characters.size, store.loadData().characters.size)
    }
}
