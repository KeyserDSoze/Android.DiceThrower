package com.keyserdsoze.dicethrower.data

import android.content.Context
import java.io.File
import org.json.JSONObject

enum class CloudMode {
    UNDECIDED,
    STANDALONE,
    GOOGLE,
}

data class GoogleAccountIdentity(
    val uniqueId: String,
    val email: String,
    val displayName: String? = null,
)

data class CloudAccountState(
    val mode: CloudMode = CloudMode.UNDECIDED,
    val account: GoogleAccountIdentity? = null,
    val driveAppDataAuthorized: Boolean = false,
    val initialReconciliationPending: Boolean = false,
) {
    val onboardingRequired: Boolean
        get() = mode == CloudMode.UNDECIDED

    val googleConnected: Boolean
        get() = mode == CloudMode.GOOGLE && account != null && driveAppDataAuthorized
}

object CloudAccountTransitions {
    fun useStandalone(): CloudAccountState = CloudAccountState(mode = CloudMode.STANDALONE)

    fun connectionCanceled(current: CloudAccountState): CloudAccountState =
        if (current.onboardingRequired) useStandalone() else current

    fun connectGoogle(account: GoogleAccountIdentity): CloudAccountState = CloudAccountState(
        mode = CloudMode.GOOGLE,
        account = account,
        driveAppDataAuthorized = true,
        // #10 consumes this flag only after explicitly reconciling local and Drive state.
        initialReconciliationPending = true,
    )

    fun disconnect(): CloudAccountState = useStandalone()
}

class CloudAccountStore(context: Context) {
    private val fileStore = CloudAccountStateFileStore(
        File(context.noBackupFilesDir, FILE_NAME),
    )

    fun load(): CloudAccountState = fileStore.load()

    fun save(state: CloudAccountState) = fileStore.save(state)

    private companion object {
        const val FILE_NAME = "cloud_account_state_v1.json"
    }
}

internal class CloudAccountStateFileStore(private val file: File) {
    fun load(): CloudAccountState {
        val raw = runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull()
            ?: return CloudAccountState()
        return runCatching { decode(JSONObject(raw)) }.getOrDefault(CloudAccountState())
    }

    fun save(state: CloudAccountState) {
        file.parentFile?.mkdirs()
        file.writeText(encode(state).toString())
    }

    private fun encode(state: CloudAccountState): JSONObject = JSONObject()
        .put("version", 1)
        .put("mode", state.mode.name)
        .put("driveAppDataAuthorized", state.driveAppDataAuthorized)
        .put("initialReconciliationPending", state.initialReconciliationPending)
        .put("account", state.account?.let { account ->
            JSONObject()
                .put("uniqueId", account.uniqueId)
                .put("email", account.email)
                .put("displayName", account.displayName ?: JSONObject.NULL)
        } ?: JSONObject.NULL)

    private fun decode(json: JSONObject): CloudAccountState {
        val mode = runCatching { CloudMode.valueOf(json.optString("mode")) }
            .getOrDefault(CloudMode.UNDECIDED)
        val accountObject = json.optJSONObject("account")
        val account = accountObject?.let {
            GoogleAccountIdentity(
                uniqueId = it.optString("uniqueId"),
                email = it.optString("email"),
                displayName = if (it.isNull("displayName")) null else it.optString("displayName").takeIf(String::isNotBlank),
            )
        }?.takeIf { it.uniqueId.isNotBlank() && it.email.isNotBlank() }
        val authorized = json.optBoolean("driveAppDataAuthorized", false)
        val reconciliationPending = json.optBoolean("initialReconciliationPending", false)

        return when {
            mode != CloudMode.GOOGLE -> CloudAccountState(mode = mode)
            account == null || !authorized -> CloudAccountState()
            else -> CloudAccountState(
                mode = mode,
                account = account,
                driveAppDataAuthorized = authorized,
                initialReconciliationPending = reconciliationPending,
            )
        }
    }
}
