package com.keyserdsoze.dicethrower

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.keyserdsoze.dicethrower.data.CloudAccountState
import com.keyserdsoze.dicethrower.data.CloudAccountStore
import com.keyserdsoze.dicethrower.data.CloudAccountTransitions
import com.keyserdsoze.dicethrower.data.GoogleAccountIdentity
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.ui.dice3d.Dice3DOverlayHost
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import com.keyserdsoze.dicethrower.ui.v2.DiceThrowerAppV2
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore
    private lateinit var cloudAccountStore: CloudAccountStore
    private lateinit var googleAccountCoordinator: GoogleAccountCoordinator
    private var settings by mutableStateOf(AppSettings())
    private var selectedLanguage by mutableStateOf(AppLocaleManager.SYSTEM)
    private var cloudAccountState by mutableStateOf(CloudAccountState())
    private var accountFlowBusy by mutableStateOf(false)
    private var accountFailure by mutableStateOf<GoogleConnectionFailure?>(null)
    private var pendingGoogleAccount: GoogleAccountIdentity? = null

    private val driveAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val authorized = result.resultCode == Activity.RESULT_OK &&
            googleAccountCoordinator.finishDriveAuthorization(result.data)
        if (authorized) {
            completeGoogleConnection()
        } else {
            cancelGoogleConnection()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLocaleManager.syncFrameworkLocale(this)
        store = LocalStore(this)
        cloudAccountStore = CloudAccountStore(this)
        googleAccountCoordinator = GoogleAccountCoordinator(this)
        settings = store.loadSettings()
        cloudAccountState = cloudAccountStore.load()
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
                        cloudAccountState = cloudAccountState,
                        accountFlowBusy = accountFlowBusy,
                        accountFailure = accountFailure,
                        onSettingsChanged = {
                            settings = it
                            store.saveSettings(it)
                        },
                        onUseStandalone = {
                            updateCloudAccountState(CloudAccountTransitions.useStandalone())
                            accountFailure = null
                        },
                        onConnectGoogle = ::connectGoogle,
                        onDisconnectGoogle = ::disconnectGoogle,
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

    private fun connectGoogle() {
        if (accountFlowBusy) return
        accountFlowBusy = true
        accountFailure = null
        lifecycleScope.launch {
            when (val result = googleAccountCoordinator.signIn(getString(R.string.google_web_client_id))) {
                is GoogleSignInResult.Success -> {
                    pendingGoogleAccount = result.account
                    googleAccountCoordinator.requestDriveAuthorization(
                        accountEmail = result.account.email,
                        onAuthorized = ::completeGoogleConnection,
                        onResolution = { request -> driveAuthorizationLauncher.launch(request) },
                        onFailure = {
                            pendingGoogleAccount = null
                            accountFlowBusy = false
                            accountFailure = GoogleConnectionFailure.DRIVE_AUTHORIZATION
                        },
                    )
                }
                GoogleSignInResult.Canceled -> cancelGoogleConnection()
                is GoogleSignInResult.Failure -> {
                    accountFlowBusy = false
                    accountFailure = result.reason
                }
            }
        }
    }

    private fun completeGoogleConnection() {
        val account = pendingGoogleAccount ?: run {
            accountFlowBusy = false
            accountFailure = GoogleConnectionFailure.DRIVE_AUTHORIZATION
            return
        }
        pendingGoogleAccount = null
        updateCloudAccountState(CloudAccountTransitions.connectGoogle(account))
        accountFlowBusy = false
        accountFailure = null
    }

    private fun cancelGoogleConnection() {
        pendingGoogleAccount = null
        updateCloudAccountState(CloudAccountTransitions.connectionCanceled(cloudAccountState))
        accountFlowBusy = false
        accountFailure = null
    }

    private fun disconnectGoogle() {
        if (accountFlowBusy) return
        val email = cloudAccountState.account?.email ?: return
        accountFlowBusy = true
        accountFailure = null
        googleAccountCoordinator.disconnect(
            accountEmail = email,
            onComplete = {
                lifecycleScope.launch {
                    googleAccountCoordinator.clearCredentialSession()
                    updateCloudAccountState(CloudAccountTransitions.disconnect())
                    accountFlowBusy = false
                }
            },
            onFailure = {
                accountFlowBusy = false
                accountFailure = GoogleConnectionFailure.DISCONNECT
            },
        )
    }

    private fun updateCloudAccountState(state: CloudAccountState) {
        cloudAccountState = state
        cloudAccountStore.save(state)
    }
}
