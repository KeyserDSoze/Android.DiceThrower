package com.keyserdsoze.dicethrower.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionedDocumentTest {
    @Test
    fun everyWriteAdvancesTheVersionAndStaleCopiesAreRefused() {
        val prefs = InMemoryPreferences()
        val document = VersionedDocument(prefs, "data", "data_version")
        assertNull(document.read())
        assertEquals(0L, document.version())

        // Screen A loads the document; an import then saves from another snapshot.
        val loadedByScreen = document.version()
        document.write("""{"characters":["imported"]}""")
        assertEquals(1L, document.version())

        // Screen A's next save is based on the old copy and must not overwrite the import.
        val stale = runCatching { document.requireVersion(loadedByScreen) }.exceptionOrNull()
        assertTrue(stale is StaleDataException)
        assertEquals("""{"characters":["imported"]}""", document.read())

        // After reloading, the same screen saves normally; callers without a token are not checked.
        document.requireVersion(document.version())
        document.requireVersion(null)
        document.write("updated") { putString("settings", "kept") }
        assertEquals(2L, document.version())
        assertEquals("updated", document.read())
        assertEquals("kept", prefs.getString("settings", null))
    }

    private class InMemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            values[key] as MutableSet<String>? ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
        override fun contains(key: String): Boolean = key in values
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private fun stage(key: String, value: Any?): SharedPreferences.Editor {
                pending[key] = value
                return this
            }
            override fun putString(key: String, value: String?): SharedPreferences.Editor = stage(key, value)
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = stage(key, values)
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = stage(key, value)
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = stage(key, value)
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = stage(key, value)
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = stage(key, value)
            override fun remove(key: String): SharedPreferences.Editor = stage(key, null)
            override fun clear(): SharedPreferences.Editor {
                values.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                pending.clear()
            }
        }
    }
}
