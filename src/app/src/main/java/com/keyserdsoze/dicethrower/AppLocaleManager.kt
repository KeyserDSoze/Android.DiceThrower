package com.keyserdsoze.dicethrower

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class AppLanguageOption(val code: String, val nativeName: String)

object AppLocaleManager {
    const val SYSTEM = "system"
    private const val PREFS = "dice_thrower_locale"
    private const val KEY_LANGUAGE = "language"

    val supportedLanguages = listOf(
        AppLanguageOption("en", "English"),
        AppLanguageOption("it", "Italiano"),
        AppLanguageOption("es", "Español"),
        AppLanguageOption("fr", "Français"),
        AppLanguageOption("de", "Deutsch"),
        AppLanguageOption("pt", "Português"),
        AppLanguageOption("ru", "Русский"),
        AppLanguageOption("ar", "العربية"),
        AppLanguageOption("hi", "हिन्दी"),
        AppLanguageOption("zh-CN", "简体中文"),
        AppLanguageOption("ja", "日本語"),
        AppLanguageOption("ko", "한국어"),
        AppLanguageOption("id", "Bahasa Indonesia"),
        AppLanguageOption("tr", "Türkçe"),
        AppLanguageOption("vi", "Tiếng Việt"),
        AppLanguageOption("bn", "বাংলা"),
        AppLanguageOption("ur", "اردو"),
        AppLanguageOption("fa", "فارسی"),
        AppLanguageOption("pl", "Polski"),
        AppLanguageOption("nl", "Nederlands"),
        AppLanguageOption("th", "ไทย"),
        AppLanguageOption("ms", "Bahasa Melayu"),
        AppLanguageOption("sw", "Kiswahili"),
        AppLanguageOption("ta", "தமிழ்"),
        AppLanguageOption("te", "తెలుగు"),
        AppLanguageOption("mr", "मराठी"),
        AppLanguageOption("pa", "ਪੰਜਾਬੀ"),
        AppLanguageOption("gu", "ગુજરાતી"),
        AppLanguageOption("kn", "ಕನ್ನಡ"),
        AppLanguageOption("ml", "മലയാളം"),
        AppLanguageOption("my", "မြန်မာ"),
        AppLanguageOption("ne", "नेपाली"),
        AppLanguageOption("uk", "Українська"),
        AppLanguageOption("he", "עברית"),
        AppLanguageOption("el", "Ελληνικά"),
        AppLanguageOption("ro", "Română"),
        AppLanguageOption("cs", "Čeština"),
        AppLanguageOption("hu", "Magyar"),
        AppLanguageOption("sv", "Svenska"),
        AppLanguageOption("ha", "Hausa"),
    )

    private val supportedCodes = supportedLanguages.map { it.code }.toSet()

    fun selectedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM)
            ?.takeIf { it == SYSTEM || it in supportedCodes }
            ?: SYSTEM

    fun setLanguage(context: Context, code: String) {
        val normalized = if (code == SYSTEM || code in supportedCodes) code else SYSTEM
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, normalized).apply()

        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (normalized == SYSTEM) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(normalized)
        }
    }

    fun syncFrameworkLocale(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val selected = selectedLanguage(context)
        val desired = if (selected == SYSTEM) LocaleList.getEmptyLocaleList()
        else LocaleList.forLanguageTags(selected)
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager.applicationLocales.toLanguageTags() != desired.toLanguageTags()) {
            manager.applicationLocales = desired
        }
    }

    fun wrap(base: Context): Context {
        val selected = selectedLanguage(base)
        if (selected == SYSTEM) return base
        val locale = Locale.forLanguageTag(selected)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }
}
