package com.keyserdsoze.dicethrower

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.data.AppBackupCodec
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import com.keyserdsoze.dicethrower.ui.v2.ArcaneBackground
import com.keyserdsoze.dicethrower.ui.v2.BrandIcon
import com.keyserdsoze.dicethrower.ui.v2.PremiumCard

class BackupActivity : ComponentActivity() {
    private lateinit var store: LocalStore

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLocaleManager.syncFrameworkLocale(this)
        store = LocalStore(this)
        enableEdgeToEdge()

        setContent {
            val settings = remember { store.loadSettings() }
            DiceThrowerTheme(themeMode = settings.themeMode) {
                BackupScreen(
                    store = store,
                    onClose = { finish() },
                    onRestoreCommitted = { payload ->
                        store.replaceAll(payload.data, payload.settings, payload.imageAssets)
                        AppLocaleManager.setLanguage(this, payload.language)
                        val restart = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restart)
                        finishAffinity()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupScreen(
    store: LocalStore,
    onClose: () -> Unit,
    onRestoreCommitted: (AppBackupCodec.BackupPayload) -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<AppBackupCodec.BackupPayload?>(null) }
    val exportSuccess = stringResource(R.string.backup_export_success)
    val unknownError = stringResource(R.string.backup_unknown_error)
    val invalidFile = stringResource(R.string.backup_invalid_file)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val data = store.loadData()
                val raw = AppBackupCodec.encode(
                    data = data,
                    settings = store.loadSettings(),
                    language = AppLocaleManager.selectedLanguage(context),
                    imageAssets = store.backupImageAssets(data),
                )
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter().use { writer ->
                    requireNotNull(writer) { "Unable to open backup destination" }
                    writer.write(raw)
                }
            }.onSuccess {
                error = null
                status = exportSuccess
            }.onFailure { failure ->
                status = null
                error = failure.message ?: unknownError
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                    requireNotNull(reader) { "Unable to open backup file" }
                    reader.readText()
                }
                AppBackupCodec.decode(raw)
            }.onSuccess { payload ->
                error = null
                status = null
                pendingImport = payload
            }.onFailure { failure ->
                status = null
                error = failure.message ?: invalidFile
            }
        }
    }

    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    ),
                    title = {
                        Text(
                            stringResource(R.string.backup_restore_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onClose) {
                            Text(stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BrandIcon(size = 72)
                            Column(
                                modifier = Modifier.padding(start = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = "Dice Thrower",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.backup_restore_intro),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.backup_export_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.backup_export_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { exportLauncher.launch(DEFAULT_BACKUP_FILE_NAME) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.backup_export_action))
                            }
                        }
                    }
                }

                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.backup_import_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.backup_import_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf(
                                            BACKUP_MIME_TYPE,
                                            "application/json",
                                            "text/json",
                                            "text/plain",
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.backup_import_action))
                            }
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.backup_image_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                status?.let { message ->
                    item {
                        PremiumCard(Modifier.fillMaxWidth()) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                error?.let { message ->
                    item {
                        PremiumCard(Modifier.fillMaxWidth()) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingImport?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_import_confirm_body,
                        payload.data.characters.size,
                        payload.data.rolls.size,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImport = null
                        onRestoreCommitted(payload)
                    },
                ) {
                    Text(stringResource(R.string.backup_import_replace_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private const val BACKUP_MIME_TYPE = "application/vnd.keyserdsoze.dicethrower.backup+json"
private const val DEFAULT_BACKUP_FILE_NAME = "dice-thrower-backup.json"
