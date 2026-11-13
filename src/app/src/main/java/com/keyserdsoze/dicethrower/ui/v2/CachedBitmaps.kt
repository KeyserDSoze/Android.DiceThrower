package com.keyserdsoze.dicethrower.ui.v2

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.data.CharacterImageAssetStore
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Process-wide caches for table artwork and decoded image assets, so painting a 1080x1920 table
 * or decoding a 10 MiB image happens once and never on the main thread. Pure presentation state,
 * independent from dice math. Cached bitmaps are shared: callers must never recycle them.
 */
internal object DiceBitmapCache {
    private const val FULL_TABLE_BYTES =
        FantasyTableArtwork.FULL_WIDTH * FantasyTableArtwork.FULL_HEIGHT * 4

    /** At most two full-size tables. */
    private val fullSize = bitmapCache(2 * FULL_TABLE_BYTES)

    /** Every table preview plus sampled avatars and custom-table previews. */
    private val small = bitmapCache(16 * 1024 * 1024)

    @Volatile
    private var trimCallbacksRegistered = false

    private fun bitmapCache(maxBytes: Int) = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private fun cacheFor(full: Boolean) = if (full) fullSize else small

    fun peek(key: String, full: Boolean): Bitmap? =
        cacheFor(full).get(key)?.takeUnless(Bitmap::isRecycled)

    /** Blocking: call from a background dispatcher. Oversized results are returned uncached. */
    fun getOrPut(context: Context, key: String, full: Boolean, load: () -> Bitmap?): Bitmap? {
        peek(key, full)?.let { return it }
        registerTrimCallbacks(context)
        val cache = cacheFor(full)
        val loaded = load() ?: return null
        // A single entry larger than half the budget would only evict everything else.
        if (loaded.allocationByteCount <= cache.maxSize() / 2) cache.put(key, loaded)
        return loaded
    }

    private fun registerTrimCallbacks(context: Context) {
        if (trimCallbacksRegistered) return
        synchronized(this) {
            if (trimCallbacksRegistered) return
            context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) clear()
                }

                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = clear()
            })
            trimCallbacksRegistered = true
        }
    }

    private fun clear() {
        fullSize.evictAll()
        small.evictAll()
    }
}

/** Largest power-of-two sample size that keeps both decoded dimensions at least the target. */
internal fun sampleSizeFor(width: Int, height: Int, minWidth: Int, minHeight: Int): Int {
    if (width <= 0 || height <= 0) return 1
    val targetWidth = minWidth.coerceAtLeast(1)
    val targetHeight = minHeight.coerceAtLeast(1)
    var sample = 1
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
    return sample
}

/** Decodes [bytes] without upscaling below [minWidth]x[minHeight], the size it is displayed at. */
internal fun decodeSampledBitmap(bytes: ByteArray, minWidth: Int, minHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, minWidth, minHeight)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/** Full-screen custom table: the renderer center-crops it, so keep up to 2048 px per side. */
internal fun decodeTableBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private fun decodeSampledUri(context: Context, raw: String, minWidth: Int, minHeight: Int): Bitmap? =
    runCatching {
        val uri = Uri.parse(raw)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, minWidth, minHeight)
        }
        context.contentResolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()

/**
 * Bitmap for [key], taken from [DiceBitmapCache] or produced by [load] on [dispatcher].
 * Null while loading and when no image is available.
 */
@Composable
private fun produceCachedBitmap(
    context: Context,
    key: String?,
    full: Boolean,
    dispatcher: CoroutineDispatcher,
    load: () -> Bitmap?,
): Bitmap? {
    val bitmap by produceState(key?.let { DiceBitmapCache.peek(it, full) }, key) {
        value = if (key == null) null else DiceBitmapCache.peek(key, full) ?: withContext(dispatcher) {
            runCatching { DiceBitmapCache.getOrPut(context, key, full, load) }.getOrNull()
        }
    }
    return bitmap
}

/** Bundled or painted table preview for the table picker. */
@Composable
internal fun rememberTablePreviewBitmap(context: Context, theme: DiceTableTheme): Bitmap? =
    produceCachedBitmap(context, "preset:${theme.name}:preview", full = false, Dispatchers.Default) {
        theme.presetBitmap(context, preview = true)
    }

/**
 * Full-size roll table. A custom image replaces the preset, which is then never painted unless
 * the image can no longer be read.
 */
@Composable
internal fun rememberRollTableBitmap(
    context: Context,
    theme: DiceTableTheme,
    image: CharacterImageRef?,
): Bitmap? {
    val presetKey = "preset:${theme.name}:full"
    val customKey = image?.let { "table:${it.assetId}" }
    val bitmap by produceState(DiceBitmapCache.peek(customKey ?: presetKey, full = true), theme, image) {
        value = DiceBitmapCache.peek(customKey ?: presetKey, full = true) ?: withContext(Dispatchers.IO) {
            runCatching {
                image?.let { ref ->
                    DiceBitmapCache.getOrPut(context, "table:${ref.assetId}", full = true) {
                        CharacterImageAssetStore(context).loadVerified(ref)?.let(::decodeTableBitmap)
                    }
                } ?: DiceBitmapCache.getOrPut(context, presetKey, full = true) { theme.presetBitmap(context) }
            }.getOrNull()
        }
    }
    return bitmap
}

/** Custom table image sampled for its full-width preview card (aspect ratio 1.9). */
@Composable
internal fun rememberCustomTablePreviewBitmap(context: Context, image: CharacterImageRef?): Bitmap? {
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val heightPx = (widthPx / 1.9f).roundToInt()
    return produceCachedBitmap(
        context, image?.let { "table-preview:${it.assetId}:${widthPx}x$heightPx" }, full = false, Dispatchers.IO,
    ) {
        image?.let { ref ->
            CharacterImageAssetStore(context).loadVerified(ref)?.let { decodeSampledBitmap(it, widthPx, heightPx) }
        }
    }
}

/** Character portrait sampled for a [sizePx] avatar; legacy document URIs keep working. */
@Composable
internal fun rememberAvatarBitmap(
    context: Context,
    image: CharacterImageRef?,
    legacyUri: String?,
    sizePx: Int,
): Bitmap? {
    val key = image?.let { "avatar:${it.assetId}:$sizePx" } ?: legacyUri?.let { "avatar-uri:$it:$sizePx" }
    return produceCachedBitmap(context, key, full = false, Dispatchers.IO) {
        image?.let { ref ->
            CharacterImageAssetStore(context).loadVerified(ref)?.let { decodeSampledBitmap(it, sizePx, sizePx) }
        } ?: legacyUri?.let { decodeSampledUri(context, it, sizePx, sizePx) }
    }
}
