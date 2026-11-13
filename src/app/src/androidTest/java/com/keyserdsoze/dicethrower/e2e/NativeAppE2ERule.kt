package com.keyserdsoze.dicethrower.e2e

import android.content.pm.ApplicationInfo
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.keyserdsoze.dicethrower.AppLocaleManager
import com.keyserdsoze.dicethrower.MainActivity
import com.keyserdsoze.dicethrower.data.CloudAccountState
import com.keyserdsoze.dicethrower.data.CloudAccountStore
import com.keyserdsoze.dicethrower.data.CloudAccountTransitions
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.data.sync.LocalSyncJournal
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.ui.AppDataViewModel
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/** Runs the real Activity. Captures diagnostics before closing it, without masking test failures. */
internal class NativeAppE2ERule(private val compose: ComposeTestRule) : TestRule {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    val store get() = LocalStore(context)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var output: File
    private val steps = StringBuilder()

    fun launch(data: AppData, settings: AppSettings, locale: String = "en") {
        check(scenario == null)
        step("Seed standalone fixture; locale=$locale; animations=${settings.animationsEnabled}")
        CloudAccountStore(context).save(CloudAccountTransitions.useStandalone())
        AppLocaleManager.setLanguage(context, locale)
        store.replaceAll(data, settings)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppDataViewModel
        scenario!!.onActivity { model = ViewModelProvider(it)[AppDataViewModel::class.java] }
        compose.waitUntil(10_000) { model.ready }
    }

    fun recreate() {
        step("Recreate Activity")
        checkNotNull(scenario).recreate()
    }

    fun rotate() {
        step("Change portrait/landscape orientation")
        val active = checkNotNull(scenario)
        lateinit var before: MainActivity
        var expected = Configuration.ORIENTATION_LANDSCAPE
        active.onActivity { activity ->
            before = activity
            val wasLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            expected = if (wasLandscape) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            activity.requestedOrientation = if (wasLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(10_000) {
            runCatching {
                var rotated = false
                active.onActivity { activity ->
                    rotated = activity !== before && activity.resources.configuration.orientation == expected
                }
                rotated
            }.getOrDefault(false)
        }
    }

    fun assertSystemBarIcons(darkIcons: Boolean) {
        step("Expect dark system-bar icons: $darkIcons")
        checkNotNull(scenario).onActivity { activity ->
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertEquals("Status-bar icon contrast", darkIcons, controller.isAppearanceLightStatusBars)
            assertEquals("Navigation-bar icon contrast", darkIcons, controller.isAppearanceLightNavigationBars)
        }
    }

    fun step(message: String) {
        steps.append(SystemClock.uptimeMillis()).append("  ").append(message).append('\n')
        output.resolve("steps.txt").writeText(steps.toString())
    }

    fun checkpoint(name: String) {
        step("Checkpoint: $name")
        if (InstrumentationRegistry.getArguments().getString("e2eScreenshots") == "true") {
            compose.waitForIdle()
            // Wait for native accessibility/window updates to become idle before pixel capture.
            // Unlike a fixed sleep, this reacts to the current UI activity and is bounded.
            runCatching { instrumentation.uiAutomation.waitForIdle(100, 1500) }
            screenshot(name)
        }
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            output.resolve("$name.png").outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun captureFailure(failure: Throwable) {
        // These operations are best-effort: the original assertion/exception remains the failure.
        runCatching { output.resolve("failure.txt").writeText(failure.stackTraceToString()) }
        runCatching { instrumentation.uiAutomation.waitForIdle(100, 1500) }
        runCatching { screenshot("failure") }
        runCatching {
            // Modal sheets/dialogs can expose more than one Compose root.
            val tree = runCatching {
                compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString()
            }.getOrElse { "Compose hierarchy unavailable: ${it.message}" }
            output.resolve("semantics.txt").writeText(tree)
        }
        runCatching {
            // Capture only diagnostic tags, not general Play Services/authentication logs.
            val command = "logcat -d -t 1500 -v threadtime AndroidRuntime:E Dice3DScene:D TestRunner:I *:S"
            val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                output.resolve("logcat.txt").outputStream().use { input.copyTo(it) }
            }
        }
    }

    private fun publishArtifacts() {
        // UTP uninstalls the test/target APKs at the end of the run. Copy generated diagnostics
        // with the instrumentation shell before teardown; no app storage permission is needed.
        fun shell(command: String) {
            val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        val destination = "/sdcard/dice-thrower-e2e-artifacts"
        shell("mkdir -p $destination")
        // The source path is app-owned and the run name was sanitized above (no user input).
        shell("cp -R ${output.absolutePath} $destination/")
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            assumeTrue("E2E requires a debug APK on a disposable emulator/device",
                context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            val account = CloudAccountStore(context).load()
            assumeFalse("Do not seed E2E fixtures over a connected Google account", account.googleConnected)
            val original = store.loadData()
            // Do not let fixture replacement/orphan cleanup touch user-owned image files.
            assumeTrue("Run E2E on a disposable installation without personal images",
                original.characters.all { it.image == null && it.imageUri == null && it.diceTableImage == null })
            val preserved = OriginalState(original, store.loadSettings(), store.loadSyncJournal(),
                account, AppLocaleManager.selectedLanguage(context))
            val name = "${description.className.substringAfterLast('.')}-${description.methodName}-${System.currentTimeMillis()}"
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            output = File(context.getExternalFilesDir(null) ?: context.cacheDir, "e2e-artifacts/$name")
            check(output.mkdirs() || output.isDirectory)
            steps.clear()
            output.resolve("device.txt").writeText(
                "${description.displayName}\nSDK=${Build.VERSION.SDK_INT}\nmodel=${Build.MODEL}\n",
            )
            var originalFailure: Throwable? = null
            try {
                base.evaluate()
            } catch (failure: Throwable) {
                originalFailure = failure
                captureFailure(failure)
                throw failure
            } finally {
                runCatching { publishArtifacts() }
                try {
                    scenario?.close()
                    scenario = null
                    store.withDataLock {
                        // Restore the original graph/metadata exactly, without recording deletions
                        // or neutralizing its base revisions as a user backup restore would.
                        store.applySyncedData(preserved.data, emptyList(), acceptedBaseline = preserved.data)
                        store.applySyncedSettings(preserved.settings)
                        store.saveSyncJournal(preserved.journal)
                    }
                    CloudAccountStore(context).save(preserved.account)
                    AppLocaleManager.setLanguage(context, preserved.locale)
                    assertEquals("Fixture restored data", preserved.data, store.loadData())
                    assertEquals("Fixture restored settings", preserved.settings, store.loadSettings())
                    assertEquals("Fixture restored sync journal", preserved.journal, store.loadSyncJournal())
                    assertEquals("Fixture restored account choice", preserved.account, CloudAccountStore(context).load())
                    assertEquals("Fixture restored language", preserved.locale, AppLocaleManager.selectedLanguage(context))
                } catch (cleanup: Throwable) {
                    if (originalFailure != null) originalFailure.addSuppressed(cleanup) else throw cleanup
                }
            }
        }
    }

    private data class OriginalState(
        val data: AppData,
        val settings: AppSettings,
        val journal: LocalSyncJournal,
        val account: CloudAccountState,
        val locale: String,
    )
}
