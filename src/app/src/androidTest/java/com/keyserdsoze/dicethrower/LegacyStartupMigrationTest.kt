package com.keyserdsoze.dicethrower

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage for the v0.0.5 cold-start crash reported on real v5 user data. */
@RunWith(AndroidJUnit4::class)
class LegacyStartupMigrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun seedVersionFiveCharacterData() {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(DATA_KEY, versionFiveData().toString())
            .commit()
    }

    @After
    fun clearSeededData() {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun appColdStartsWithVersionFiveCharacterRevision() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
        }
    }

    private fun versionFiveData(): JSONObject = JSONObject()
        .put("version", 5)
        .put("characters", JSONArray().put(
            JSONObject()
                .put("id", "character")
                .put("name", "Alyndra")
                .put("tag", "")
                .put("level", 1)
                .put("order", 0),
        ))
        .put("modifiers", JSONArray())
        .put("groups", JSONArray())
        .put("rolls", JSONArray())
        .put("logs", JSONArray())
        .put("diceStyles", JSONArray())
        .put("characterSyncMetadata", JSONArray().put(
            JSONObject()
                .put("characterId", "character")
                .put("updatedAt", 100L)
                .put("revision", LEGACY_REVISION)
                .put("writerId", "legacy-writer"),
        ))

    private companion object {
        const val STORE = "dice_thrower_store"
        const val DATA_KEY = "data_v1"
        const val LEGACY_REVISION = "afa7f3bff7c22213e5bc6cfaa8ed142f13a8dc1de74f0630533769e2a94bec1b"
    }
}
