package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Same portable-file bound as links/JSON decoding; the caller owns dispatcher selection. */
internal object CharacterFileReader {
    fun decode(stream: InputStream, checkCancelled: () -> Unit = {}): AppData = stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            checkCancelled()
            val length = input.read(buffer)
            if (length == -1) break
            require(output.size() + length <= CharacterTransferCodec.MAX_RAW_BYTES) { "Character file too large" }
            output.write(buffer, 0, length)
        }
        checkCancelled()
        CharacterTransferCodec.decodeJson(output.toString(Charsets.UTF_8.name()))
    }
}
