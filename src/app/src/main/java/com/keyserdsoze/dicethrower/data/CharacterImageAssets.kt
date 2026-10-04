package com.keyserdsoze.dicethrower.data

import android.content.Context
import android.net.Uri
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLConnection
import java.security.MessageDigest

data class PortableCharacterImageAsset(
    val ref: CharacterImageRef,
    val bytes: ByteArray,
)

object CharacterImageAssets {
    const val MAX_IMAGE_BYTES: Int = 10 * 1024 * 1024
    const val ORPHAN_RETENTION_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
    private val hashRegex = Regex("[0-9a-f]{64}")

    fun createRef(bytes: ByteArray, mimeType: String): CharacterImageRef {
        require(bytes.isNotEmpty()) { "Character image cannot be empty" }
        require(bytes.size <= MAX_IMAGE_BYTES) { "Character image exceeds 10 MiB" }
        val normalizedMime = mimeType.trim().lowercase()
        require(normalizedMime.startsWith("image/")) { "Character asset must use an image MIME type" }
        val hash = sha256(bytes)
        return CharacterImageRef(
            assetId = assetIdForHash(hash),
            sha256 = hash,
            mimeType = normalizedMime,
            byteSize = bytes.size.toLong(),
        )
    }

    fun validate(ref: CharacterImageRef, bytes: ByteArray): Boolean =
        hashRegex.matches(ref.sha256) &&
            ref.assetId == assetIdForHash(ref.sha256) &&
            ref.mimeType.startsWith("image/") &&
            ref.byteSize == bytes.size.toLong() &&
            bytes.isNotEmpty() &&
            bytes.size <= MAX_IMAGE_BYTES &&
            sha256(bytes) == ref.sha256

    fun referencedAssetIds(data: AppData): Set<String> =
        data.characters.mapNotNull { it.image?.assetId }.toSet()

    fun orphanAssetIds(availableAssetIds: Collection<String>, data: AppData): Set<String> =
        availableAssetIds.toSet() - referencedAssetIds(data)

    fun requirePortableAssetsMatch(
        data: AppData,
        assets: List<PortableCharacterImageAsset>,
    ) {
        val refs = data.characters.mapNotNull { it.image }
        val assetsById = assets.associateBy { it.ref.assetId }
        require(assetsById.size == assets.size) { "Portable payload contains duplicate character image assets" }
        require(assetsById.keys == refs.mapTo(mutableSetOf()) { it.assetId }) {
            "Portable character image assets do not match character references"
        }
        refs.forEach { ref ->
            val asset = requireNotNull(assetsById[ref.assetId]) { "Missing character image asset ${ref.assetId}" }
            require(validate(asset.ref, asset.bytes) && validate(ref, asset.bytes)) {
                "Broken character image asset ${ref.assetId}"
            }
        }
    }

    fun assetIdForHash(hash: String): String {
        require(hashRegex.matches(hash)) { "Invalid SHA-256 hash" }
        return "img_$hash"
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}

object CharacterImageMigration {
    fun migrateLegacy(
        data: AppData,
        importLegacy: (String) -> CharacterImageRef?,
    ): AppData {
        var changed = false
        val characters = data.characters.map { character ->
            if (character.image != null || character.imageUri.isNullOrBlank()) return@map character
            val ref = importLegacy(character.imageUri) ?: return@map character
            changed = true
            character.copy(imageUri = null, image = ref)
        }
        return if (changed) data.copy(characters = characters) else data
    }
}

internal class CharacterImageAssetFileStore(private val directory: File) {
    fun save(ref: CharacterImageRef, bytes: ByteArray) {
        require(CharacterImageAssets.validate(ref, bytes)) { "Character image asset is corrupt" }
        directory.mkdirs()
        val target = fileFor(ref.assetId)
        if (readVerified(ref) != null) return

        val temporary = File(directory, ".${ref.assetId}.tmp")
        temporary.writeBytes(bytes)
        if (target.exists()) target.delete()
        require(temporary.renameTo(target)) { "Unable to store character image asset" }
    }

    fun readVerified(ref: CharacterImageRef): ByteArray? {
        val file = fileFor(ref.assetId)
        if (!file.isFile || file.length() != ref.byteSize || file.length() > CharacterImageAssets.MAX_IMAGE_BYTES) {
            return null
        }
        return runCatching { file.readBytes() }
            .getOrNull()
            ?.takeIf { CharacterImageAssets.validate(ref, it) }
    }

    fun availableAssetIds(): Set<String> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.startsWith("img_") }
        .mapTo(mutableSetOf()) { it.name }

    fun cleanupOrphans(data: AppData, now: Long, retentionMillis: Long = CharacterImageAssets.ORPHAN_RETENTION_MILLIS) {
        val orphanIds = CharacterImageAssets.orphanAssetIds(availableAssetIds(), data)
        orphanIds.forEach { assetId ->
            val file = fileFor(assetId)
            val age = (now - file.lastModified()).coerceAtLeast(0L)
            if (age >= retentionMillis) file.delete()
        }
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".tmp") }
            .filter { (now - it.lastModified()).coerceAtLeast(0L) >= retentionMillis }
            .forEach(File::delete)
    }

    private fun fileFor(assetId: String): File {
        require(assetId.matches(Regex("img_[0-9a-f]{64}"))) { "Invalid character image asset ID" }
        return File(directory, assetId)
    }
}

class CharacterImageAssetStore(context: Context) {
    private val contentResolver = context.contentResolver
    private val files = CharacterImageAssetFileStore(File(context.filesDir, DIRECTORY_NAME))

    fun importFromUri(uri: Uri): CharacterImageRef {
        val bytes = contentResolver.openInputStream(uri)?.use(::readLimited)
            ?: throw IllegalArgumentException("Unable to read selected character image")
        val mimeType = contentResolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: URLConnection.guessContentTypeFromStream(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("Unable to determine character image type")
        val ref = CharacterImageAssets.createRef(bytes, mimeType)
        files.save(ref, bytes)
        return ref
    }

    fun loadVerified(ref: CharacterImageRef): ByteArray? = files.readVerified(ref)

    fun migrateLegacyImages(data: AppData): AppData {
        return CharacterImageMigration.migrateLegacy(data) { rawUri ->
            runCatching { importFromUri(Uri.parse(rawUri)) }.getOrNull()
        }
    }

    fun exportReferenced(data: AppData): List<PortableCharacterImageAsset> {
        return data.characters.mapNotNull { it.image }.distinctBy { it.assetId }.map { ref ->
            val bytes = files.readVerified(ref)
                ?: throw IllegalArgumentException("Missing or corrupt character image asset ${ref.assetId}")
            PortableCharacterImageAsset(ref, bytes)
        }
    }

    fun restorePortableAssets(assets: List<PortableCharacterImageAsset>) {
        assets.forEach { asset -> files.save(asset.ref, asset.bytes) }
    }

    fun requireReferencedAssetsAvailable(data: AppData) {
        data.characters.mapNotNull { it.image }.distinctBy { it.assetId }.forEach { ref ->
            require(files.readVerified(ref) != null) { "Missing or corrupt character image asset ${ref.assetId}" }
        }
    }

    fun cleanupOrphans(data: AppData, now: Long = System.currentTimeMillis()) {
        files.cleanupOrphans(data, now)
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= CharacterImageAssets.MAX_IMAGE_BYTES) { "Character image exceeds 10 MiB" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val DIRECTORY_NAME = "character-images"
    }
}
