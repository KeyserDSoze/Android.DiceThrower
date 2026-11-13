package com.keyserdsoze.dicethrower

import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keyserdsoze.dicethrower.data.CloudAccountStore
import com.keyserdsoze.dicethrower.data.CloudAccountTransitions
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.ui.AppDataViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppDataRecreationTest {
    @get:Rule val composeRule = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = LocalStore(context)

    @Before
    fun seedStandaloneApp() {
        AppLocaleManager.setLanguage(context, "en")
        CloudAccountStore(context).save(CloudAccountTransitions.useStandalone())
        store.replaceAll(AppData(), AppSettings(animationsEnabled = false))
    }

    @After
    fun clearFixture() {
        store.replaceAll(AppData(), AppSettings())
        CloudAccountStore(context).save(CloudAccountTransitions.useStandalone())
        AppLocaleManager.setLanguage(context, AppLocaleManager.SYSTEM)
    }

    @Test
    fun pendingOptimisticEditUsesTheSameViewModelAfterActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: AppDataViewModel
            scenario.onActivity { model = ViewModelProvider(it)[AppDataViewModel::class.java] }
            composeRule.waitUntil(10_000) { model.ready }
            val initialVersion = store.dataVersion()
            val locked = CountDownLatch(1)
            val release = CountDownLatch(1)
            val blocker = Thread {
                store.withDataLock {
                    locked.countDown()
                    check(release.await(20, TimeUnit.SECONDS))
                }
            }.apply { start() }
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS))
                scenario.onActivity {
                    model.save(model.data.copy(characters = listOf(CharacterProfile("pending", "Pending edit"))))
                    assertEquals("Pending edit", model.data.characters.single().name)
                }
                scenario.recreate()
                scenario.onActivity {
                    val recreated = ViewModelProvider(it)[AppDataViewModel::class.java]
                    assertSame(model, recreated)
                    assertEquals("Pending edit", recreated.data.characters.single().name)
                }
            } finally {
                release.countDown()
                blocker.join(5_000)
            }
            composeRule.waitUntil(10_000) { store.loadData().characters.singleOrNull()?.name == "Pending edit" }
            assertEquals(initialVersion + 1, store.dataVersion())
        }
    }

    @Test
    fun characterDialogRestoresFieldsAndDoesNotCreateAnotherCharacterOnRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: AppDataViewModel
            scenario.onActivity { model = ViewModelProvider(it)[AppDataViewModel::class.java] }
            composeRule.waitUntil(10_000) { model.ready }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("New character").performClick()
            composeRule.onNodeWithText("Name").performTextReplacement("Rotating heroine")
            composeRule.onNodeWithText("Tag").performTextReplacement("Arcane test")
            composeRule.onNodeWithText("Level").performTextReplacement("7")

            scenario.recreate()
            composeRule.onNodeWithText("Name").assertTextContains("Rotating heroine")
            composeRule.onNodeWithText("Tag").assertTextContains("Arcane test")
            composeRule.onNodeWithText("Level").assertTextContains("7")
            composeRule.onNodeWithText("Create").performClick()
            composeRule.waitUntil(10_000) { store.loadData().characters.size == 1 }
            scenario.recreate()
            composeRule.onNodeWithText("Name").assertDoesNotExist()
            val saved = store.loadData().characters.single()
            assertEquals("Rotating heroine", saved.name)
            assertEquals("Arcane test", saved.tag)
            assertEquals(7, saved.level)
        }
    }
}
