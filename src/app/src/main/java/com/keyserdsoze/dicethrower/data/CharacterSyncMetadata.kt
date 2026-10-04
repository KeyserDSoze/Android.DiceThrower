package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterSyncMetadata
import java.security.MessageDigest

enum class SyncChangeState {
    SAME,
    LOCAL_ONLY,
    REMOTE_ONLY,
    CONFLICT,
}

object CharacterRevision {
    fun canonicalContent(data: AppData, characterId: String): String {
        val character = requireNotNull(data.characters.firstOrNull { it.id == characterId }) {
            "Unknown character $characterId"
        }
        val canonical = StringBuilder()

        canonical.section("character")
        canonical.field("id", character.id)
        canonical.field("name", character.name)
        // Legacy document URIs are local migration sources, never syncable content identity.
        canonical.field("imageUri", null)
        character.image?.let { image ->
            canonical.field("imageAssetId", image.assetId)
            canonical.field("imageSha256", image.sha256)
            canonical.field("imageMimeType", image.mimeType)
            canonical.field("imageByteSize", image.byteSize)
        }
        canonical.field("tag", character.tag)
        canonical.field("level", character.level)
        canonical.field("order", character.order)
        canonical.field("defaultDiceStyleId", character.defaultDiceStyleId)

        data.modifiers.filter { it.characterId == characterId }.sortedBy { it.id }.forEach { modifier ->
            canonical.section("modifier")
            canonical.field("id", modifier.id)
            canonical.field("name", modifier.name)
            canonical.field("value", modifier.value)
            canonical.field("order", modifier.order)
        }
        data.groups.filter { it.characterId == characterId }.sortedBy { it.id }.forEach { group ->
            canonical.section("group")
            canonical.field("id", group.id)
            canonical.field("name", group.name)
            canonical.field("order", group.order)
        }
        data.rolls.filter { it.characterId == characterId }.sortedBy { it.id }.forEach { roll ->
            canonical.section("roll")
            canonical.field("id", roll.id)
            canonical.field("name", roll.name)
            canonical.field("expression", roll.expression)
            canonical.field("groupId", roll.groupId)
            canonical.field("enabled", roll.enabled)
            canonical.field("order", roll.order)
            roll.levelRules.sortedBy { it.id }.forEach { rule ->
                canonical.section("levelRule")
                canonical.field("id", rule.id)
                canonical.field("kind", rule.kind.name)
                canonical.field("trigger", rule.trigger)
                canonical.field("expression", rule.expression)
            }
            canonical.section("diceAppearance")
            canonical.field("mode", roll.diceAppearance.mode.name)
            canonical.field("styleId", roll.diceAppearance.styleId)
            roll.diceAppearance.perDieStyleIds.toSortedMap().forEach { (slot, styleId) ->
                canonical.field("perDie:$slot", styleId)
            }
            roll.diceAppearance.randomStyleIds.sorted().forEach { styleId ->
                canonical.field("randomStyleId", styleId)
            }
        }
        data.diceStyles.filter { it.characterId == characterId }.sortedBy { it.id }.forEach { style ->
            canonical.section("diceStyle")
            canonical.field("id", style.id)
            canonical.field("name", style.name)
            canonical.field("material", style.material.name)
            canonical.field("primaryColorArgb", style.primaryColorArgb)
            canonical.field("secondaryColorArgb", style.secondaryColorArgb)
            canonical.field("order", style.order)
        }
        data.logs.filter { it.characterId == characterId }.sortedBy { it.id }.forEach { log ->
            canonical.section("rollLog")
            canonical.field("id", log.id)
            canonical.field("rollDefinitionId", log.rollDefinitionId)
            canonical.field("rollName", log.rollName)
            canonical.field("expression", log.expression)
            canonical.field("total", log.total)
            canonical.field("detail", log.detail)
            canonical.field("timestamp", log.timestamp)
        }

        return canonical.toString()
    }

    fun revision(data: AppData, characterId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalContent(data, characterId).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun StringBuilder.section(name: String) {
        append("S")
        appendToken(name)
    }

    private fun StringBuilder.field(name: String, value: Any?) {
        append("F")
        appendToken(name)
        if (value == null) {
            append("N")
        } else {
            append("V")
            appendToken(value.toString())
        }
    }

    private fun StringBuilder.appendToken(value: String) {
        append(value.length)
        append(':')
        append(value)
    }
}

object SyncMetadataManager {
    private val revisionRegex = Regex("[0-9a-f]{64}")

    fun reconcileLocalEdit(
        previous: AppData,
        proposed: AppData,
        writerId: String,
        now: Long,
    ): AppData {
        require(writerId.isNotBlank()) { "Writer ID cannot be blank" }
        require(now >= 0L) { "updatedAt cannot be negative" }

        val previousMetadata = previous.characterSyncMetadata.associateBy { it.characterId }
        val metadata = proposed.characters.map { character ->
            val revision = CharacterRevision.revision(proposed, character.id)
            val prior = previousMetadata[character.id]
            if (prior != null && prior.revision == revision) {
                prior
            } else {
                CharacterSyncMetadata(
                    characterId = character.id,
                    updatedAt = monotonicUpdatedAt(now, prior?.updatedAt),
                    revision = revision,
                    writerId = writerId,
                    baseRevision = prior?.baseRevision,
                )
            }
        }.sortedBy { it.characterId }

        return proposed.copy(characterSyncMetadata = metadata)
    }

    fun ensureMetadata(
        data: AppData,
        writerId: String,
        now: Long,
    ): AppData = reconcileLocalEdit(data, data, writerId, now)

    fun markSynced(
        data: AppData,
        characterId: String,
        remoteRevision: String,
    ): AppData {
        require(revisionRegex.matches(remoteRevision)) { "Remote revision must be a SHA-256 hash" }
        require(data.characters.any { it.id == characterId }) { "Unknown character $characterId" }
        val metadata = data.characterSyncMetadata.firstOrNull { it.characterId == characterId }
            ?: throw IllegalArgumentException("Character $characterId has no sync metadata")

        return data.copy(
            characterSyncMetadata = data.characterSyncMetadata.map { item ->
                if (item.characterId == characterId) item.copy(baseRevision = remoteRevision) else item
            },
        )
    }

    fun classify(
        localRevision: String,
        remoteRevision: String,
        baseRevision: String?,
    ): SyncChangeState {
        if (localRevision == remoteRevision) return SyncChangeState.SAME
        if (baseRevision == null) return SyncChangeState.CONFLICT

        val localChanged = localRevision != baseRevision
        val remoteChanged = remoteRevision != baseRevision
        return when {
            localChanged && !remoteChanged -> SyncChangeState.LOCAL_ONLY
            !localChanged && remoteChanged -> SyncChangeState.REMOTE_ONLY
            localChanged && remoteChanged -> SyncChangeState.CONFLICT
            else -> SyncChangeState.SAME
        }
    }

    private fun monotonicUpdatedAt(now: Long, previous: Long?): Long {
        if (previous == null || now > previous) return now
        return if (previous == Long.MAX_VALUE) previous else previous + 1L
    }
}
