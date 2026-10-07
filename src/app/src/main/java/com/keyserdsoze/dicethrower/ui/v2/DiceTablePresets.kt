package com.keyserdsoze.dicethrower.ui.v2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.DiceTableTheme

@DrawableRes
internal fun DiceTableTheme.presetDrawableRes(): Int = when (this) {
    DiceTableTheme.ARCANE -> R.drawable.table_arcane_night
    DiceTableTheme.OAK -> R.drawable.table_fantasy_felt
    DiceTableTheme.EMERALD -> R.drawable.table_ancient_map
    DiceTableTheme.OBSIDIAN -> R.drawable.table_scifi_neon
    // Six newer themes now use full-resolution procedural textures rather than 135x240 images.
    DiceTableTheme.TAVERN_WOOD, DiceTableTheme.DUNGEON_STONE, DiceTableTheme.ELVEN_GROVE,
    DiceTableTheme.FROZEN_REALM, DiceTableTheme.DESERT_RUINS, DiceTableTheme.ASTRAL_VOID -> 0
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

internal fun DiceTableTheme.presetBitmap(context: Context, preview: Boolean = false): Bitmap {
    if (FantasyTableArtwork.supports(this)) return FantasyTableArtwork.render(this, preview)
    val resource = presetDrawableRes()
    val options = if (preview) BitmapFactory.Options().apply { inSampleSize = 4 } else null
    return BitmapFactory.decodeResource(context.resources, resource, options)
        ?: error("Unable to decode dice-table preset $name")
}
