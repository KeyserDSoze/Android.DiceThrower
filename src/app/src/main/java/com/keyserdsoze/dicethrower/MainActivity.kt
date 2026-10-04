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
import com.keyserdsoze.dicethrower.data.cloud.CloudRepositoryFactory
import com.keyserdsoze.dicethrower.data.cloud.CloudRemoteRepository
import com.keyserdsoze.dicethrower.data.sync.AndroidSyncLocalGateway
import com.keyserdsoze.dicethrower.data.sync.CloudSyncEngine
import com.keyserdsoze.dicethrower.data.sync.SyncConflict
import com.keyserdsoze.dicethrower.data.sync.SyncConflictResolution
import com.keyserdsoze.dicethrower.data.sync.SyncErrorKind
import com.keyserdsoze.dicethrower.data.sync.SyncStatus
import com.keyserdsoze.dicethrower.data.sync.SyncStatusKind
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.ui.dice3d.Dice3DOverlayHost
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import com.keyserdsoze.dicethrower.ui.v2.DiceThrowerAppV2
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore
    private lateinit var cloudAccountStore: CloudAccountStore
    private lateinit var googleAccountCoordinator: GoogleAccountCoordinator
    private lateinit var syncEngine: CloudSyncEngine
    private lateinit var cloudRepository: CloudRemoteRepository
    private var settings by mutableStateOf(AppSettings())
    private var selectedLanguage by mutableStateOf(AppLocaleManager.SYSTEM)
    private var cloudAccountState by mutableStateOf(CloudAccountState())
    private var accountFlowBusy by mutableStateOf(false)
    private var accountFailure by mutableStateOf<GoogleConnectionFailure?>(null)
    private var pendingGoogleAccount: GoogleAccountIdentity? = null
    private var syncStatus by mutableStateOf(SyncStatus(SyncStatusKind.LOCAL_ONLY))
    private var dataRefreshVersion by mutableStateOf(0)
    private var syncJob: Job? = null
    private var cloudDeleteBusy by mutableStateOf(false)
    private var cloudDeleteFailed by mutableStateOf(false)

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
        cloudRepository = CloudRepositoryFactory.create(this) { cloudAccountState }
        syncEngine = CloudSyncEngine(
            local = AndroidSyncLocalGateway(store),
            remote = cloudRepository,
        )
        syncStatus = syncEngine.currentStatus(cloudAccountState.googleConnected)
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
                        syncStatus = syncStatus,
                        cloudDeleteBusy = cloudDeleteBusy,
                        cloudDeleteFailed = cloudDeleteFailed,
                        dataRefreshVersion = dataRefreshVersion,
                        onSettingsChanged = {
                            settings = it
                            store.saveSettings(it)
                            refreshPendingStatus()
                        },
                        onLocalDataChanged = ::refreshPendingStatus,
                        onSyncNow = ::syncNow,
                        onResolveConflict = ::resolveConflict,
                        onUseStandalone = {
                            updateCloudAccountState(CloudAccountTransitions.useStandalone())
                            accountFailure = null
                        },
                        onConnectGoogle = ::connectGoogle,
                        onDisconnectGoogle = ::disconnectGoogle,
                        onDeleteCloudData = ::deleteCloudData,
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

    override fun onResume() {
        super.onResume()
        if (::syncEngine.isInitialized && cloudAccountState.googleConnected) syncNow()
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
        syncNow()
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
                    syncStatus = SyncStatus(SyncStatusKind.LOCAL_ONLY)
                    accountFlowBusy = false
                }
            },
            onFailure = {
                accountFlowBusy = false
                accountFailure = GoogleConnectionFailure.DISCONNECT
            },
        )
    }

    private fun deleteCloudData() {
        if (!cloudAccountState.googleConnected || cloudDeleteBusy) return
        val email = cloudAccountState.account?.email ?: return
        cloudDeleteBusy = true
        cloudDeleteFailed = false
        accountFailure = null
        lifecycleScope.launch {
            try {
                cloudRepository.deleteAllData()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                cloudDeleteBusy = false
                cloudDeleteFailed = true
                return@launch
            }

            // Stop sync before revoking the grant so the intentionally deleted dataset cannot be re-uploaded.
            updateCloudAccountState(CloudAccountTransitions.useStandalone())
            syncStatus = SyncStatus(SyncStatusKind.LOCAL_ONLY)
            googleAccountCoordinator.disconnect(
                accountEmail = email,
                onComplete = {
                    lifecycleScope.launch {
                        googleAccountCoordinator.clearCredentialSession()
                        cloudDeleteBusy = false
                    }
                },
                onFailure = {
                    cloudDeleteBusy = false
                    accountFailure = GoogleConnectionFailure.DISCONNECT
                },
            )
        }
    }

    private fun updateCloudAccountState(state: CloudAccountState) {
        cloudAccountState = state
        cloudAccountStore.save(state)
    }

    private fun refreshPendingStatus() {
        syncStatus = syncEngine.currentStatus(cloudAccountState.googleConnected)
    }

    private fun syncNow() {
        syncNow(emptyMap())
    }

    private fun resolveConflict(conflict: SyncConflict, resolution: SyncConflictResolution) {
        val key = conflict.characterId ?: CloudSyncEngine.SETTINGS_CONFLICT_KEY
        syncNow(mapOf(key to resolution))
    }

    private fun syncNow(resolutions: Map<String, SyncConflictResolution>) {
        if (!cloudAccountState.googleConnected || syncJob?.isActive == true) return
        syncStatus = syncStatus.copy(kind = SyncStatusKind.SYNCING, error = null, conflicts = emptyList())
        syncJob = lifecycleScope.launch {
            val result = syncEngine.sync(settings.conflictPolicy, resolutions)
            if (result.status.error == SyncErrorKind.AUTHORIZATION) {
                updateCloudAccountState(CloudAccountTransitions.authorizationRevoked())
                syncStatus = SyncStatus(SyncStatusKind.LOCAL_ONLY)
                accountFailure = GoogleConnectionFailure.DRIVE_AUTHORIZATION
            } else {
                syncStatus = result.status
            }
            if (result.localDataChanged) dataRefreshVersion++
            if (result.localSettingsChanged) settings = store.loadSettings()
            if (result.initialReconciliationComplete && cloudAccountState.initialReconciliationPending) {
                updateCloudAccountState(cloudAccountState.copy(initialReconciliationPending = false))
            }
        }
    }
}
