package com.keyserdsoze.dicethrower

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.keyserdsoze.dicethrower.data.CloudAccountState
import com.keyserdsoze.dicethrower.data.CloudAccountStore
import com.keyserdsoze.dicethrower.data.CloudAccountTransitions
import com.keyserdsoze.dicethrower.data.GoogleAccountIdentity
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.data.CharacterTransferCodec
import com.keyserdsoze.dicethrower.model.AppData
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
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import com.keyserdsoze.dicethrower.ui.AppDataViewModel
import com.keyserdsoze.dicethrower.ui.v2.DiceThrowerAppV2
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STATE_LINK_IMPORT_PENDING = "dicethrower.linkImportPending"
private const val STATE_PENDING_ACCOUNT_ID = "dicethrower.pendingAccountId"
private const val STATE_PENDING_ACCOUNT_EMAIL = "dicethrower.pendingAccountEmail"
private const val STATE_PENDING_ACCOUNT_NAME = "dicethrower.pendingAccountName"

/**
 * A dicethrower://import launch link is handled on a fresh launch only, never when Android
 * relaunches the task from Recents with its original intent, nor when the Activity is recreated
 * (rotation, language change) unless that link's import preview was still awaiting a decision.
 */
internal fun shouldHandleLaunchLink(restored: Boolean, importPreviewPending: Boolean, intentFlags: Int): Boolean =
    if (restored) importPreviewPending
    else (intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore
    private lateinit var dataModel: AppDataViewModel
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
    private var driveConsentLaunched = false
    private var syncStatus by mutableStateOf(SyncStatus(SyncStatusKind.LOCAL_ONLY))
    private var dataRefreshVersion by mutableStateOf(0)
    private var syncJob: Job? = null
    private var pendingStatusJob: Job? = null
    private var cloudDeleteBusy by mutableStateOf(false)
    private var cloudDeleteFailed by mutableStateOf(false)
    private var pendingCharacterImport by mutableStateOf<AppData?>(null)
    private var characterImportError by mutableStateOf(false)
    /** True while [pendingCharacterImport] comes from the current intent's import link. */
    private var linkImportPending = false

    private val characterFileImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                val output = java.io.ByteArrayOutputStream()
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Unable to open character file")
                stream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val length = input.read(buffer)
                        if (length == -1) break
                        require(output.size() + length <= 128 * 1024) { "Character file too large" }
                        output.write(buffer, 0, length)
                    }
                }
                finishLinkImport()
                pendingCharacterImport = CharacterTransferCodec.decodeJson(
                    output.toString(Charsets.UTF_8.name()),
                )
                characterImportError = false
            } catch (_: Exception) {
                pendingCharacterImport = null
                characterImportError = true
            } catch (_: StackOverflowError) {
                // Untrusted file: deeply nested JSON must never crash the app.
                pendingCharacterImport = null
                characterImportError = true
            }
        }
    }

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
        dataModel = ViewModelProvider(this, AppDataViewModel.Factory(this))[AppDataViewModel::class.java]
        cloudAccountStore = CloudAccountStore(this)
        googleAccountCoordinator = GoogleAccountCoordinator(this)
        settings = store.loadSettings()
        cloudAccountState = cloudAccountStore.load()
        cloudRepository = CloudRepositoryFactory.create(this) { cloudAccountState }
        syncEngine = CloudSyncEngine(
            local = AndroidSyncLocalGateway(store),
            remote = cloudRepository,
        )
        requireReconciliationIfJournalSaysSo()
        refreshPendingStatus()
        // Recreated while the Drive consent screen was open: its result arrives after this.
        val restoredEmail = savedInstanceState?.getString(STATE_PENDING_ACCOUNT_EMAIL)
        if (restoredEmail != null) {
            pendingGoogleAccount = GoogleAccountIdentity(
                uniqueId = savedInstanceState.getString(STATE_PENDING_ACCOUNT_ID).orEmpty(),
                email = restoredEmail,
                displayName = savedInstanceState.getString(STATE_PENDING_ACCOUNT_NAME),
            )
            driveConsentLaunched = true
            accountFlowBusy = true
        }
        selectedLanguage = AppLocaleManager.selectedLanguage(this)
        enableEdgeToEdge()
        // Handle the launch link once: not again after rotation, recreate() or a relaunch from
        // Recents, unless its import preview was still waiting for the user's decision.
        if (shouldHandleLaunchLink(
                restored = savedInstanceState != null,
                importPreviewPending = savedInstanceState?.getBoolean(STATE_LINK_IMPORT_PENDING) == true,
                intentFlags = intent.flags,
            )) {
            handleIncomingCharacterLink(intent)
        }

        setContent {
            DiceThrowerTheme(themeMode = settings.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DiceThrowerAppV2(
                        dataModel = dataModel,
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
                        onImportCharacterFile = {
                            characterFileImportLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
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
                                AppLocaleManager.setLanguage(this@MainActivity, code)
                                selectedLanguage = code
                                recreate()
                            }
                        },
                    )

                    pendingCharacterImport?.let { incoming ->
                        CharacterImportPreviewDialog(
                            incoming = incoming,
                            onDismiss = {
                                pendingCharacterImport = null
                                finishLinkImport()
                            },
                            onImport = { name, level ->
                                try {
                                    val configured = CharacterTransferCodec.withRecipientPreferences(
                                        incoming, name, level,
                                    )
                                    store.withDataLock {
                                        val merged = CharacterTransferCodec.importCopy(
                                            store.loadData(), configured,
                                        )
                                        store.saveData(merged)
                                    }
                                    dataRefreshVersion++
                                    refreshPendingStatus()
                                } catch (_: Exception) {
                                    characterImportError = true
                                }
                                pendingCharacterImport = null
                                finishLinkImport()
                            },
                        )
                    }
                    if (characterImportError) {
                        AlertDialog(
                            onDismissRequest = { characterImportError = false },
                            title = { Text(stringResource(R.string.import_character_error_title)) },
                            text = { Text(stringResource(R.string.import_character_error_message)) },
                            confirmButton = {
                                TextButton(onClick = { characterImportError = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCharacterLink(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LINK_IMPORT_PENDING, linkImportPending && pendingCharacterImport != null)
        val account = pendingGoogleAccount
        if (driveConsentLaunched && account != null) {
            outState.putString(STATE_PENDING_ACCOUNT_ID, account.uniqueId)
            outState.putString(STATE_PENDING_ACCOUNT_EMAIL, account.email)
            outState.putString(STATE_PENDING_ACCOUNT_NAME, account.displayName)
        }
    }

    private fun handleIncomingCharacterLink(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_VIEW) return
        val uri = incoming.data ?: return
        if (uri.scheme != "dicethrower" || uri.host != "import") return
        val token = uri.getQueryParameter("data")
        try {
            require(!token.isNullOrBlank()) { "Missing character payload" }
            pendingCharacterImport = CharacterTransferCodec.decodeToken(token)
            characterImportError = false
            // The preview survives a configuration change through STATE_LINK_IMPORT_PENDING;
            // finishLinkImport marks it resolved.
            linkImportPending = true
        } catch (_: Exception) {
            rejectCharacterLink()
        } catch (_: StackOverflowError) {
            // Untrusted link: deeply nested JSON must never crash the app.
            rejectCharacterLink()
        }
    }

    private fun rejectCharacterLink() {
        pendingCharacterImport = null
        characterImportError = true
        linkImportPending = false
    }

    /**
     * Marks the launch link as resolved once its import preview is confirmed or dismissed.
     * The Activity intent itself is never mutated: shouldHandleLaunchLink already prevents a
     * second import after rotation, recreate() or a relaunch from Recents.
     */
    private fun finishLinkImport() {
        linkImportPending = false
    }

    /** A rebuilt journal or a restored backup needs a full, user-confirmed reconciliation. */
    private fun requireReconciliationIfJournalSaysSo() {
        if (cloudAccountState.googleConnected && !cloudAccountState.initialReconciliationPending &&
            store.loadSyncJournal().reconciliationRequired
        ) {
            updateCloudAccountState(cloudAccountState.copy(initialReconciliationPending = true))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::syncEngine.isInitialized) requireReconciliationIfJournalSaysSo()
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
                    when (val authorization = googleAccountCoordinator.requestDriveAuthorization(result.account.email)) {
                        DriveAuthorizationResult.Authorized -> completeGoogleConnection()
                        is DriveAuthorizationResult.NeedsResolution -> {
                            // Only now does the consent screen own the pending account across rotation.
                            driveConsentLaunched = true
                            driveAuthorizationLauncher.launch(authorization.request)
                        }
                        DriveAuthorizationResult.Failed -> {
                            pendingGoogleAccount = null
                            accountFlowBusy = false
                            accountFailure = GoogleConnectionFailure.DRIVE_AUTHORIZATION
                        }
                    }
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
        driveConsentLaunched = false
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
        driveConsentLaunched = false
        pendingGoogleAccount = null
        updateCloudAccountState(CloudAccountTransitions.connectionCanceled(cloudAccountState))
        accountFlowBusy = false
        accountFailure = null
    }

    private fun disconnectGoogle() {
        if (accountFlowBusy || cloudDeleteBusy) return
        val email = cloudAccountState.account?.email ?: return
        accountFlowBusy = true
        accountFailure = null
        val running = syncJob
        lifecycleScope.launch {
            // A late sync result must not overwrite the status set below.
            running?.cancelAndJoin()
            if (googleAccountCoordinator.disconnect(email)) {
                googleAccountCoordinator.clearCredentialSession()
                updateCloudAccountState(CloudAccountTransitions.disconnect())
                syncStatus = SyncStatus(SyncStatusKind.LOCAL_ONLY)
                accountFlowBusy = false
            } else {
                accountFlowBusy = false
                accountFailure = GoogleConnectionFailure.DISCONNECT
            }
        }
    }

    private fun deleteCloudData() {
        if (!cloudAccountState.googleConnected || cloudDeleteBusy) return
        val email = cloudAccountState.account?.email ?: return
        cloudDeleteBusy = true
        cloudDeleteFailed = false
        accountFailure = null
        val running = syncJob
        lifecycleScope.launch {
            // No sync may run (or recreate files) while the dataset is being deleted.
            running?.cancelAndJoin()
            try {
                cloudRepository.deleteAllData()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                cloudDeleteBusy = false
                cloudDeleteFailed = true
                refreshPendingStatus()
                return@launch
            }

            // Stop sync before revoking the grant so the intentionally deleted dataset cannot be re-uploaded.
            updateCloudAccountState(CloudAccountTransitions.useStandalone())
            syncStatus = SyncStatus(SyncStatusKind.LOCAL_ONLY)
            if (googleAccountCoordinator.disconnect(email)) {
                googleAccountCoordinator.clearCredentialSession()
            } else {
                accountFailure = GoogleConnectionFailure.DISCONNECT
            }
            cloudDeleteBusy = false
        }
    }

    private fun updateCloudAccountState(state: CloudAccountState) {
        cloudAccountState = state
        cloudAccountStore.save(state)
    }

    private fun refreshPendingStatus() {
        pendingStatusJob?.cancel()
        val connected = cloudAccountState.googleConnected
        val previousStatus = syncStatus
        pendingStatusJob = lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) { syncEngine.currentStatus(connected) }
            // A background status refresh must not overwrite a newer sync result, disconnect or
            // deletion. Reading/hashing the local snapshot no longer runs after each edit on main.
            if (cloudAccountState.googleConnected == connected && syncStatus == previousStatus &&
                syncJob?.isActive != true && !cloudDeleteBusy
            ) {
                syncStatus = status
            }
        }
    }

    private fun syncNow() {
        syncNow(emptyMap())
    }

    private fun resolveConflict(conflict: SyncConflict, resolution: SyncConflictResolution) {
        val key = conflict.characterId ?: if (
            conflict.kind == com.keyserdsoze.dicethrower.data.sync.SyncConflictKind.DICE_CATALOG_DIVERGED
        ) CloudSyncEngine.CATALOG_CONFLICT_KEY else CloudSyncEngine.SETTINGS_CONFLICT_KEY
        syncNow(mapOf(key to resolution))
    }

    private fun syncNow(resolutions: Map<String, SyncConflictResolution>) {
        if (!cloudAccountState.googleConnected || cloudDeleteBusy || accountFlowBusy || syncJob?.isActive == true) return
        pendingStatusJob?.cancel()
        syncStatus = syncStatus.copy(kind = SyncStatusKind.SYNCING, error = null, conflicts = emptyList())
        val policy = settings.conflictPolicy
        syncJob = lifecycleScope.launch {
            // Local snapshot validation/hashing and the final sync commit also perform disk work.
            // LocalStore's shared transaction lock keeps them safe against the background writer.
            val result = withContext(Dispatchers.IO) { syncEngine.sync(policy, resolutions) }
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
