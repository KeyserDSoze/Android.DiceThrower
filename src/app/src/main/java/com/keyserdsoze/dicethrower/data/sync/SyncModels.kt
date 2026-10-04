package com.keyserdsoze.dicethrower.data.sync

import com.keyserdsoze.dicethrower.data.CharacterImageAssets
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.RollButtonPosition

data class RoamingSettings(
    val showRollButton: Boolean,
    val rollButtonPosition: RollButtonPosition,
    val logRetention: Int,
) {
    fun applyTo(local: AppSettings): AppSettings = local.copy(
        showRollButton = showRollButton,
        rollButtonPosition = rollButtonPosition,
        logRetention = logRetention,
    )

    fun revision(): String = CharacterImageAssets.sha256(
        buildString {
            append("showRollButton=").append(showRollButton).append('\n')
            append("rollButtonPosition=").append(rollButtonPosition.name).append('\n')
            append("logRetention=").append(logRetention).append('\n')
        }.toByteArray(Charsets.UTF_8),
    )

    companion object {
        fun from(settings: AppSettings): RoamingSettings = RoamingSettings(
            showRollButton = settings.showRollButton,
            rollButtonPosition = settings.rollButtonPosition,
            logRetention = settings.logRetention,
        )
    }
}

data class SettingsSyncMetadata(
    val updatedAt: Long,
    val revision: String,
    val writerId: String,
    val baseRevision: String? = null,
)

data class PendingCharacterDeletion(
    val characterId: String,
    val deletedAt: Long,
    val writerId: String,
    val baseRevision: String,
    val deletedRevision: String,
)

data class LocalSyncJournal(
    val pendingDeletions: List<PendingCharacterDeletion> = emptyList(),
    val settingsMetadata: SettingsSyncMetadata? = null,
    val lastSuccessfulSyncAt: Long? = null,
)

enum class SyncStatusKind {
    LOCAL_ONLY,
    SYNCED,
    PENDING,
    SYNCING,
    ERROR,
    CONFLICT,
}

enum class SyncConflictKind {
    CHARACTER_DIVERGED,
    LOCAL_EDIT_REMOTE_DELETE,
    LOCAL_DELETE_REMOTE_EDIT,
    REMOTE_MISSING_WITHOUT_TOMBSTONE,
    SETTINGS_DIVERGED,
}

enum class SyncConflictResolution {
    KEEP_LOCAL,
    USE_REMOTE,
}

enum class ConflictArea {
    PROFILE,
    IMAGE,
    MODIFIERS,
    ROLLS,
    DICE_STYLES,
    HISTORY,
    DELETION,
    SETTINGS,
}

data class SyncConflict(
    val kind: SyncConflictKind,
    val characterId: String? = null,
    val characterName: String? = null,
    val localRevision: String? = null,
    val remoteRevision: String? = null,
    val localUpdatedAt: Long? = null,
    val remoteUpdatedAt: Long? = null,
    val localWriterId: String? = null,
    val remoteWriterId: String? = null,
    val changedAreas: List<ConflictArea> = emptyList(),
)

enum class SyncErrorKind {
    AUTHORIZATION,
    TRANSIENT,
    SCHEMA,
    REMOTE_PROTOCOL,
    UNKNOWN,
}

data class SyncStatus(
    val kind: SyncStatusKind,
    val pendingCount: Int = 0,
    val lastSuccessfulSyncAt: Long? = null,
    val conflicts: List<SyncConflict> = emptyList(),
    val error: SyncErrorKind? = null,
    val autoResolvedCount: Int = 0,
)

data class SyncRunResult(
    val status: SyncStatus,
    val localDataChanged: Boolean = false,
    val localSettingsChanged: Boolean = false,
    val initialReconciliationComplete: Boolean = false,
)
