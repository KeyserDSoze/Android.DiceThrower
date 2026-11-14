package com.keyserdsoze.dicethrower

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keyserdsoze.dicethrower.data.CharacterTransferCodec
import com.keyserdsoze.dicethrower.data.CloudAccountStore
import com.keyserdsoze.dicethrower.data.CloudAccountTransitions
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.ui.CharacterFileImportViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class CharacterFileImportTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val incoming = AppData(characters = listOf(CharacterProfile("shared", "File preview QA", level = 7)))
    private val newer = AppData(characters = listOf(CharacterProfile("new", "Newer selection")))

    private fun model(owner: ComponentActivity, decode: suspend (Uri) -> AppData): CharacterFileImportViewModel =
        ViewModelProvider(owner, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CharacterFileImportViewModel(decode) as T
            }
        })[CharacterFileImportViewModel::class.java]

    @Test fun loadingSurvivesRealOwnerRecreationAndNeverRunsOnMain() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val thread = AtomicLong()
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var retained: CharacterFileImportViewModel
                lateinit var before: ComponentActivity
                scenario.onActivity { owner ->
                    before = owner
                    retained = model(owner) {
                        calls.incrementAndGet()
                        thread.set(Thread.currentThread().id)
                        started.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        incoming
                    }
                    retained.load(Uri.parse("content://test/one"))
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                assertNotEquals(Looper.getMainLooper().thread.id, thread.get())
                assertTrue(retained.loading)
                scenario.recreate()
                scenario.onActivity { owner ->
                    assertNotSame(before, owner)
                    assertSame(retained, model(owner) { error("Must reuse the retained loader") })
                }
                release.countDown()
                compose.waitUntil(10_000) { retained.preview == incoming }
                assertEquals(1, calls.get())
                assertFalse(retained.failed)
                assertFalse(retained.loading)
            }
        } finally { release.countDown() }
    }

    @Test fun newerSelectionWinsEvenWhenCancelledReadFinishesLater() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var state: CharacterFileImportViewModel
                scenario.onActivity { owner ->
                    state = model(owner) { uri ->
                        if (uri.lastPathSegment == "old") {
                            started.countDown()
                            try { check(release.await(10, TimeUnit.SECONDS)); incoming }
                            finally { finished.countDown() }
                        } else newer
                    }
                    state.load(Uri.parse("content://test/old"))
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                scenario.onActivity { state.load(Uri.parse("content://test/new")) }
                compose.waitUntil(10_000) { state.preview == newer }
                release.countDown()
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(newer, state.preview)
                assertFalse(state.failed)
            }
        } finally { release.countDown() }
    }

    @Test fun clearDiscardsLateErrorsAndDoesNotShowCancellationAsFailure() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var state: CharacterFileImportViewModel
                scenario.onActivity { owner ->
                    state = model(owner) {
                        started.countDown()
                        try { check(release.await(10, TimeUnit.SECONDS)); throw IOException("Old request") }
                        finally { finished.countDown() }
                    }
                    state.load(Uri.parse("content://test/old"))
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                scenario.onActivity { state.clear() }
                release.countDown()
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertNull(state.preview)
                assertFalse(state.failed)
                assertFalse(state.loading)
            }
        } finally { release.countDown() }
    }

    @Test fun decodingAndDeepNestingErrorsRemainRecoverable() {
        for (error in listOf(IOException("Unreadable"), StackOverflowError("Nested JSON"))) {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var state: CharacterFileImportViewModel
                scenario.onActivity { owner ->
                    state = model(owner) { throw error }
                    state.load(Uri.parse("content://test/bad"))
                }
                compose.waitUntil(10_000) { state.failed }
                assertNull(state.preview)
                assertFalse(state.loading)
                scenario.onActivity { state.clear() }
                assertFalse(state.failed)
            }
        }
    }

    @Test fun realFilePreviewSurvivesMainActivityRecreationAndCancelDoesNotWrite() {
        val accountStore = CloudAccountStore(context)
        val originalAccount = accountStore.load()
        assumeFalse("Use a disposable standalone installation", originalAccount.googleConnected)
        val before = LocalStore(context).loadData()
        val file = File.createTempFile("character-preview-", ".json", context.cacheDir)
        file.writeText(CharacterTransferCodec.encodeJson(incoming, "shared"))
        accountStore.save(CloudAccountTransitions.useStandalone())
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var state: CharacterFileImportViewModel
                scenario.onActivity { owner ->
                    state = ViewModelProvider(owner)[CharacterFileImportViewModel::class.java]
                    state.load(Uri.fromFile(file))
                }
                compose.waitUntil(10_000) { state.preview != null }
                compose.onNodeWithText(context.getString(R.string.import_character_title)).assertIsDisplayed()
                scenario.recreate()
                compose.onNodeWithText(context.getString(R.string.import_character_title)).assertIsDisplayed()
                assertTrue(compose.onAllNodesWithText("File preview QA").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
                compose.waitUntil(10_000) { state.preview == null }
                scenario.recreate()
                compose.onNodeWithText(context.getString(R.string.import_character_title)).assertDoesNotExist()
                assertEquals(before, LocalStore(context).loadData())
            }
        } finally {
            accountStore.save(originalAccount)
            file.delete()
        }
    }
}
