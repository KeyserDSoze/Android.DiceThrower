package com.keyserdsoze.dicethrower.ui.v2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.DiceTableTheme

internal fun DiceTableTheme.presetBitmap(context: Context): Bitmap =
    BitmapFactory.decodeResource(context.resources, presetDrawableRes())
        ?: error("Unable to decode dice-table preset $name")
