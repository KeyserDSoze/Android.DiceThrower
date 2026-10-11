package com.keyserdsoze.dicethrower.ui.v2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.data.CharacterTransferCodec
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile

/**
 * Share configuration, never history, photos, or app/cloud authentication state.
 * The QR is generated in memory; the Share sheet is opened only on explicit tap.
 */
@Composable
internal fun CharacterShareDialog(
    character: CharacterProfile,
    data: AppData,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.share_character_share_link)
    val json = remember(data, character.id) {
        runCatching { CharacterTransferCodec.encodeJson(data, character.id) }.getOrNull()
    }
    val url = remember(data, character.id) {
        runCatching { CharacterTransferCodec.shareUrlOrNull(data, character.id) }.getOrNull()
    }
    val qr = remember(url) { url?.let { runCatching { qrBitmap(it) }.getOrNull() } }
    var exportFailed by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null && json != null) {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("No output stream")
            }.isSuccess
            exportFailed = !written
            if (written) Toast.makeText(context, R.string.share_character_exported, Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_character_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(character.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.share_character_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (url != null && qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = stringResource(R.string.share_character_qr_alt),
                        modifier = Modifier.size(256.dp),
                    )
                    Text(
                        stringResource(R.string.share_character_scan_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(
                                    intent, shareChooserTitle,
                                ))
                            }.onFailure { exportFailed = true }
                        }) { Text(stringResource(R.string.share_character_share_link)) }
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Dice Thrower character", url))
                        }) { Text(stringResource(R.string.share_character_copy_link)) }
                    }
                } else {
                    Text(
                        stringResource(if (json != null) R.string.share_character_file_only else R.string.share_character_error),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (json != null) {
                    OutlinedButton(onClick = {
                        val sanitized = character.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "-").take(48)
                        exportLauncher.launch("dice-thrower-${sanitized.ifBlank { "character" }}.json")
                    }) { Text(stringResource(R.string.share_character_export_json)) }
                }
                if (exportFailed) {
                    Text(stringResource(R.string.share_character_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun qrBitmap(url: String): Bitmap {
    val dimension = 768
    val hints: Map<EncodeHintType, Any> = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 2,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, dimension, dimension, hints)
    val pixels = IntArray(dimension * dimension)
    for (y in 0 until dimension) {
        for (x in 0 until dimension) {
            pixels[y * dimension + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, dimension, dimension, Bitmap.Config.ARGB_8888)
}
