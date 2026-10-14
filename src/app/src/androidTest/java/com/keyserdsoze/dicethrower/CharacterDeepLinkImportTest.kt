package com.keyserdsoze.dicethrower

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
 * A browser's VIEW URI cold-launches MainActivity through ActivityScenario.
 * The empty Compose rule observes that UI without owning a second Activity,
 * preserving a reliable ActivityScenario teardown and avoiding test-task leaks.
 * The physical phone camera -> browser -> app journey remains tracked in #165.
 */
@RunWith(AndroidJUnit4::class)
class CharacterDeepLinkImportTest {
    @get:Rule val composeRule = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun launchFromBrowser(uri: Uri): ActivityScenario<MainActivity> =
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
            },
        )

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
        val uri = Uri.parse("dicethrower://import?data=" + Uri.encode(token))

        // Check the *implicit* URL uses the production VIEW+BROWSABLE intent
        // filter before explicitly opening the Activity in this test.
        val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        assertEquals(MainActivity::class.java.name,
            browserIntent.resolveActivity(context.packageManager)?.className)

        val store = LocalStore(context)
        val before = store.loadData()
        assertTrue(before.characters.none { it.name == name })
        launchFromBrowser(uri).use {
            composeRule.onNodeWithText(context.getString(R.string.import_character_title))
                .assertIsDisplayed()
            assertEquals(before.characters.size, store.loadData().characters.size)
            composeRule.onNodeWithText(context.getString(R.string.import_character_confirm))
                .performClick()
            composeRule.waitForIdle()
            val saved = store.loadData()
            val imported = saved.characters.single { it.name == name }
            assertEquals(7, imported.level)
            assertNotEquals(characterId, imported.id)
            val group = saved.groups.single {
                it.characterId == imported.id && it.name == "Spell Lv 3"
            }
            val roll = saved.rolls.single {
                it.characterId == imported.id && it.name == "Fireball"
            }
            assertEquals(group.id, roll.groupId)
            assertEquals("8d6", roll.expression)
            assertEquals(5, roll.minimumLevel)
        }
    }

    @Test
    fun malformedDeepLinkDisplaysErrorWithoutWritingAnyCharacter() {
        val store = LocalStore(context)
        val before = store.loadData()
        val invalid = Uri.parse("dicethrower://import?data=not-a-valid-token")
        launchFromBrowser(invalid).use {
            composeRule.onNodeWithText(context.getString(R.string.import_character_error_title))
                .assertIsDisplayed()
            assertEquals(before.characters.size, store.loadData().characters.size)
        }
    }
}
