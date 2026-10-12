package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.InflaterInputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import org.json.JSONObject

/**
 * v1 web/Android transfer: zlib-deflated UTF-8 JSON, unpadded Base64URL.
 * The transfer contains one character only; no images, logs or cloud credentials.
 * Incoming IDs are never trusted as local primary keys.
 */
object CharacterTransferCodec {
    const val FORMAT = "dice-thrower-character"
    const val FORMAT_VERSION = 1
    private const val MAX_RAW_BYTES = 128 * 1024
    private const val MAX_TOKEN_LENGTH = 120_000
    const val MAX_SHARE_URL_LENGTH = 2600
    const val WORKSHOP_URL = "https://keyserdsoze.github.io/Android.DiceThrower/workshop/"

    /**
     * Snapshot only the selected character and styles referenced by that character.
     * This is deliberately separate from full backup and from optional Drive sync.
     */
    fun exportCharacter(data: AppData, characterId: String): AppData {
        val character = data.characters.firstOrNull { it.id == characterId }
            ?: throw IllegalArgumentException("Character not found")
        val ownedRolls = data.rolls.filter { it.characterId == characterId }
        val styleIds = buildSet {
            character.defaultDiceStyleId?.let(::add)
            character.secondaryDiceStyleId?.let(::add)
            ownedRolls.forEach { roll ->
                roll.diceAppearance.styleId?.let(::add)
                addAll(roll.diceAppearance.subgroupStyleIds.values)
                addAll(roll.diceAppearance.perDieStyleIds.values)
                addAll(roll.diceAppearance.randomStyleIds)
            }
        }
        val exported = AppData(
            characters = listOf(character.copy(
                imageUri = null,
                image = null,
                diceTableImage = null,
                order = 0,
            )),
            modifiers = data.modifiers.filter { it.characterId == characterId },
            groups = data.groups.filter { it.characterId == characterId },
            rolls = ownedRolls,
            diceStyles = data.diceStyles.filter { it.id in styleIds }.map { it.copy(characterId = null) },
        )
        AppDataValidator.requireValid(exported)
        return exported
    }

    fun encodeJson(data: AppData, characterId: String): String {
        val snapshot = exportCharacter(data, characterId)
        val json = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("data", AppDataJsonCodec.encodeDataForSync(snapshot))
            .toString()
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_RAW_BYTES) {
            "Character is too large to export"
        }
        return json
    }

    fun encodeToken(data: AppData, characterId: String): String {
        val raw = encodeJson(data, characterId)
        val stream = ByteArrayOutputStream()
        DeflaterOutputStream(stream, Deflater(9)).use { out ->
            out.write(raw.toByteArray(Charsets.UTF_8))
        }
        return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(stream.toByteArray())
    }

    /**
     * URL/QR share is supported only for reasonably short payloads.
     * Call encodeJson for a SAF .json export if this returns null.
     */
    fun shareUrlOrNull(data: AppData, characterId: String): String? {
        val url = WORKSHOP_URL + "#character=" + encodeToken(data, characterId)
        return url.takeIf { it.length <= MAX_SHARE_URL_LENGTH }
    }

    fun decodeToken(token: String): AppData {
        require(token.startsWith("v1.") && token.length <= MAX_TOKEN_LENGTH) {
            "Unsupported or oversized character link"
        }
        val encoded = token.removePrefix("v1.")
        require(encoded.isNotBlank() && encoded.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_'
        }) { "Invalid character link encoding" }
        val compressed = Base64.getUrlDecoder().decode(encoded)
        val output = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(output.size() + count <= MAX_RAW_BYTES) { "Character link exceeds size limit" }
                output.write(buffer, 0, count)
            }
        }
        return decodeJson(output.toString(Charsets.UTF_8.name()))
    }

    fun decodeJson(raw: String): AppData {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_RAW_BYTES) { "Character is too large" }
        val envelope = JSONObject(raw)
        require(envelope.optString("format") == FORMAT &&
            envelope.optInt("formatVersion") == FORMAT_VERSION) {
            "Unsupported character document"
        }
        val contents = envelope.optJSONObject("data")
            ?: throw IllegalArgumentException("Character data is missing")
        require(contents.optJSONArray("characters")?.length() == 1) { "Expected one character" }
        require((contents.optJSONArray("logs")?.length() ?: 0) == 0) { "Roll history cannot be imported" }
        require((contents.optJSONArray("characterSyncMetadata")?.length() ?: 0) == 0) {
            "Cloud metadata cannot be imported"
        }
        val characterJson = contents.getJSONArray("characters").getJSONObject(0)
        require(listOf("image", "imageUri", "diceTableImage").all {
            !characterJson.has(it) || characterJson.isNull(it)
        }) { "Embedded or external images are not supported" }
        val data = AppDataJsonCodec.decodeData(contents)
        val character = data.characters.single()
        require(character.name.isNotBlank() && character.name.length <= 160 && character.id.length in 1..180)
        require(character.level in 1..9999)
        require(data.modifiers.size <= 250 && data.groups.size <= 250 &&
            data.rolls.size <= 150 && data.diceStyles.size <= 250)
        require((data.modifiers.map { it.characterId } + data.groups.map { it.characterId } +
            data.rolls.map { it.characterId }).all { it == character.id }) {
            "Transfer contains data owned by other characters"
        }
        require(data.diceStyles.all { it.characterId == null || it.characterId == character.id }) {
            "Transfer contains unrelated dice styles"
        }
        require(data.rolls.all {
            it.subgroups.size <= 30 && it.levelRules.size <= 40 && it.effects.size <= 40
        }) { "Too many roll rules" }
        require(data.characters.none { it.image != null || it.imageUri != null || it.diceTableImage != null })
        AppDataValidator.requireValid(data)
        return data
    }

    /**
     * Apply explicit recipient preferences before import. The source document remains
     * untouched; its roll templates and future level gates are never discarded.
     */
    fun withRecipientPreferences(incoming: AppData, name: String, level: Int): AppData {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty() && normalizedName.length <= 160) {
            "Character name must be 1..160 characters"
        }
        require(level in 1..9999) { "Invalid character level" }
        require(incoming.characters.size == 1) { "Expected exactly one character" }
        val changed = incoming.copy(
            characters = listOf(incoming.characters.single().copy(
                name = normalizedName,
                level = level,
            )),
        )
        AppDataValidator.requireValid(changed)
        return changed
    }

    /** Append a completely independent character, preserving existing data and sync metadata. */
    fun importCopy(existing: AppData, incoming: AppData): AppData {
        AppDataValidator.requireValid(incoming)
        require(incoming.characters.size == 1 && incoming.logs.isEmpty() && incoming.characterSyncMetadata.isEmpty())
        val sourceId = incoming.characters.single().id

        // CharacterDataOperations already remaps Part-ID references embedded in effects,
        // appearance assignments, roll groups and level rules. Treat shared incoming
        // styles as owned temporarily to force fresh IDs even if two payloads collide.
        val temporary = incoming.copy(diceStyles = incoming.diceStyles.map { it.copy(characterId = sourceId) })
        val duplicated = CharacterDataOperations.duplicateCharacter(
            temporary, sourceId, temporary.characters.single().name,
        )
        val fresh = duplicated.characters.single { it.id != sourceId }
        val merged = existing.copy(
            characters = existing.characters + fresh.copy(order = existing.characters.size),
            modifiers = existing.modifiers + duplicated.modifiers.filter { it.characterId == fresh.id },
            groups = existing.groups + duplicated.groups.filter { it.characterId == fresh.id },
            rolls = existing.rolls + duplicated.rolls.filter { it.characterId == fresh.id },
            diceStyles = existing.diceStyles + duplicated.diceStyles
                .filter { it.characterId == fresh.id }.map { it.copy(characterId = null) },
        )
        AppDataValidator.requireValid(merged)
        return merged
    }
}
