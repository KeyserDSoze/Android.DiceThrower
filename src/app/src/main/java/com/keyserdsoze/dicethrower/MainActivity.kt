package com.keyserdsoze.dicethrower

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.ui.dice3d.Dice3DOverlayHost
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import com.keyserdsoze.dicethrower.ui.v2.DiceThrowerAppV2

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore
    private var settings by mutableStateOf(AppSettings())
    private var selectedLanguage by mutableStateOf(AppLocaleManager.SYSTEM)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLocaleManager.syncFrameworkLocale(this)
        store = LocalStore(this)
        settings = store.loadSettings()
        selectedLanguage = AppLocaleManager.selectedLanguage(this)
        enableEdgeToEdge()

        setContent {
            DiceThrowerTheme(themeMode = settings.themeMode) {
                Dice3DOverlayHost(enabled = settings.animationsEnabled) {
                    DiceThrowerAppV2(
                        store = store,
                        settings = settings,
                        selectedLanguage = selectedLanguage,
                        languageOptions = AppLocaleManager.supportedLanguages,
                        onSettingsChanged = {
                            settings = it
                            store.saveSettings(it)
                        },
                        onLanguageChanged = { code ->
                            if (code != selectedLanguage) {
                                AppLocaleManager.setLanguage(this, code)
                                selectedLanguage = code
                                recreate()
                            }
                        },
                    )
                }
            }
        }
    }
}
