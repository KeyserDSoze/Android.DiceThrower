package com.keyserdsoze.dicethrower.data

import android.content.Context
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadData(): AppData {
        val raw = prefs.getString(KEY_DATA, null) ?: return AppData()
        return runCatching {
            AppDataJsonCodec.decodeData(JSONObject(raw))
        }.getOrDefault(AppData())
    }

    fun saveData(data: AppData) {
        prefs.edit()
            .putString(KEY_DATA, AppDataJsonCodec.encodeData(data).toString())
            .apply()
    }

    fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching {
            AppDataJsonCodec.decodeSettings(JSONObject(raw))
        }.getOrDefault(AppSettings())
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_SETTINGS, AppDataJsonCodec.encodeSettings(settings).toString())
            .apply()
    }

    companion object {
        private const val PREFS = "dice_thrower_store"
        private const val KEY_DATA = "data_v1"
        private const val KEY_SETTINGS = "settings_v1"
    }
}
