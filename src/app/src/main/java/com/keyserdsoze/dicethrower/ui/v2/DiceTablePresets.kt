package com.keyserdsoze.dicethrower.ui.v2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.DiceTableTheme

/**
 * The original enum values are intentionally kept stable because they are persisted in backups
 * and sync. New premium presets append new values; existing values never change meaning.
 */
private data class DiceTableCrop(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

@DrawableRes
internal fun DiceTableTheme.presetDrawableRes(): Int = when (this) {
    DiceTableTheme.ARCANE -> R.drawable.table_arcane_night
    DiceTableTheme.OAK -> R.drawable.table_fantasy_felt
    DiceTableTheme.EMERALD -> R.drawable.table_ancient_map
    DiceTableTheme.OBSIDIAN -> R.drawable.table_scifi_neon
    DiceTableTheme.TAVERN_WOOD,
    DiceTableTheme.DUNGEON_STONE,
    DiceTableTheme.ELVEN_GROVE,
    DiceTableTheme.FROZEN_REALM,
    DiceTableTheme.DESERT_RUINS,
    DiceTableTheme.ASTRAL_VOID,
    -> R.drawable.table_premium_pack_2
}

@StringRes
internal fun DiceTableTheme.presetNameRes(): Int = when (this) {
    DiceTableTheme.ARCANE -> R.string.table_arcane
    DiceTableTheme.OAK -> R.string.table_oak
    DiceTableTheme.EMERALD -> R.string.table_emerald
    DiceTableTheme.OBSIDIAN -> R.string.table_obsidian
    DiceTableTheme.TAVERN_WOOD -> R.string.table_tavern_wood
    DiceTableTheme.DUNGEON_STONE -> R.string.table_dungeon_stone
    DiceTableTheme.ELVEN_GROVE -> R.string.table_elven_grove
    DiceTableTheme.FROZEN_REALM -> R.string.table_frozen_realm
    DiceTableTheme.DESERT_RUINS -> R.string.table_desert_ruins
    DiceTableTheme.ASTRAL_VOID -> R.string.table_astral_void
}

/**
 * Pack 2 is a 600x704 contact sheet. Only the six 180x320 artwork regions are exposed to users;
 * gutters and labels are never rendered.
 */
private fun DiceTableTheme.presetCrop(): DiceTableCrop? = when (this) {
    DiceTableTheme.ASTRAL_VOID -> DiceTableCrop(10, 10, 180, 320)
    DiceTableTheme.DESERT_RUINS -> DiceTableCrop(210, 10, 180, 320)
    DiceTableTheme.DUNGEON_STONE -> DiceTableCrop(410, 10, 180, 320)
    DiceTableTheme.ELVEN_GROVE -> DiceTableCrop(10, 360, 180, 320)
    DiceTableTheme.FROZEN_REALM -> DiceTableCrop(210, 360, 180, 320)
    DiceTableTheme.TAVERN_WOOD -> DiceTableCrop(410, 360, 180, 320)
    else -> null
}

internal fun DiceTableTheme.presetBitmap(context: Context): Bitmap {
    val source = BitmapFactory.decodeResource(context.resources, presetDrawableRes())
        ?: error("Unable to decode dice-table preset $name")
    val crop = presetCrop() ?: return source
    val safeLeft = crop.left.coerceIn(0, source.width - 1)
    val safeTop = crop.top.coerceIn(0, source.height - 1)
    val safeWidth = crop.width.coerceAtMost(source.width - safeLeft)
    val safeHeight = crop.height.coerceAtMost(source.height - safeTop)
    val result = Bitmap.createBitmap(source, safeLeft, safeTop, safeWidth, safeHeight)
    if (result !== source) source.recycle()
    return result
}
