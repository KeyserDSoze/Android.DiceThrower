package com.keyserdsoze.dicethrower.ui.v2

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.DiceTableTheme

/**
 * The enum values are intentionally kept stable because they are persisted in backups and sync.
 * Their visual presentation is free to evolve without requiring a data migration.
 */
@DrawableRes
internal fun DiceTableTheme.presetDrawableRes(): Int = when (this) {
    DiceTableTheme.ARCANE -> R.drawable.table_arcane_night
    DiceTableTheme.OAK -> R.drawable.table_fantasy_felt
    DiceTableTheme.EMERALD -> R.drawable.table_ancient_map
    DiceTableTheme.OBSIDIAN -> R.drawable.table_scifi_neon
}

@StringRes
internal fun DiceTableTheme.presetNameRes(): Int = when (this) {
    DiceTableTheme.ARCANE -> R.string.table_arcane
    DiceTableTheme.OAK -> R.string.table_oak
    DiceTableTheme.EMERALD -> R.string.table_emerald
    DiceTableTheme.OBSIDIAN -> R.string.table_obsidian
}
