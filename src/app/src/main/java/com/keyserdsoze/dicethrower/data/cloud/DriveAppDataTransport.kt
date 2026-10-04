package com.keyserdsoze.dicethrower.data.cloud

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DriveAppDataFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
)

interface DriveAccessTokenProvider {
    suspend fun accessToken(): String
}

interface DriveAppDataTransport {
    suspend fun list(): List<DriveAppDataFile>
    suspend fun read(fileId: String): ByteArray
    suspend fun create(
        name: String,
        mimeType: String,
        appProperties: Map<String, String>,
        bytes: ByteArray,
    ): DriveAppDataFile

    suspend fun update(
        fileId: String,
        name: String,
        mimeType: String,
        appProperties: Map<String, String>,
        bytes: ByteArray,
    ): DriveAppDataFile

    suspend fun delete(fileId: String)
}

internal object DriveHttpErrorPolicy {
    fun toException(statusCode: Int, responseBody: String): CloudRepositoryException = when {
        statusCode == 401 -> CloudAuthorizationException("Google Drive access token is invalid or expired")
        statusCode == 403 && isRateLimit(responseBody) ->
            CloudTransientException("Google Drive rate limit exceeded", statusCode)
        statusCode == 403 -> CloudAuthorizationException("Google Drive app-data access is forbidden")
        statusCode == 404 -> CloudNotFoundException("Google Drive app-data file was not found")
        statusCode == 429 || statusCode in 500..504 ->
            CloudTransientException("Temporary Google Drive failure (HTTP $statusCode)", statusCode)
        else -> CloudProtocolException("Google Drive request failed with HTTP $statusCode")
    }

    private fun isRateLimit(body: String): Boolean =
        body.contains("rateLimitExceeded") || body.contains("userRateLimitExceeded")
}

internal object DriveRetryPolicy {
    const val MAX_ATTEMPTS = 4
    fun delayMillis(attempt: Int, jitterMillis: Long): Long {
        require(attempt >= 0)
        return ((1L shl attempt.coerceAtMost(3)) * 1_000L + jitterMillis.coerceIn(0L, 250L))
            .coerceAtMost(8_250L)
    }
}

class DriveRestTransport(
    private val tokenProvider: DriveAccessTokenProvider,
    private val sleeper: (Long) -> Unit = Thread::sleep,
    private val jitterMillis: () -> Long = { (0L..250L).random() },
) : DriveAppDataTransport {
    override suspend fun list(): List<DriveAppDataFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<DriveAppDataFile>()
        var pageToken: String? = null
        do {
            val parameters = linkedMapOf(
                "spaces" to "appDataFolder",
                "q" to "trashed=false",
                "pageSize" to "1000",
                "fields" to "nextPageToken,files(id,name,mimeType,modifiedTime,appProperties)",
            )
            pageToken?.let { parameters["pageToken"] = it }
            val response = execute(
                method = "GET",
                url = "$FILES_URL?${encodeQuery(parameters)}",
                safeToRetry = true,
            )
            val root = parseJson(response.bytes, "Drive file list")
            val array = root.optJSONArray("files") ?: JSONArray()
            repeat(array.length()) { files += decodeFile(array.getJSONObject(it)) }
            pageToken = root.optString("nextPageToken").takeIf(String::isNotBlank)
        } while (pageToken != null)
        files
    }

    override suspend fun read(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        execute(
            method = "GET",
            url = "$FILES_URL/${path(fileId)}?alt=media",
            safeToRetry = true,
        ).bytes
    }

    override suspend fun create(
        name: String,
        mimeType: String,
        appProperties: Map<String, String>,
        bytes: ByteArray,
    ): DriveAppDataFile = withContext(Dispatchers.IO) {
        val metadata = fileMetadataJson(name, mimeType, appProperties).put("parents", JSONArray().put("appDataFolder"))
        val boundary = "dice_thrower_${UUID.randomUUID()}"
        val body = multipartBody(boundary, metadata, mimeType, bytes)
        val response = execute(
            method = "POST",
            url = "$UPLOAD_FILES_URL?uploadType=multipart&fields=id,name,mimeType,modifiedTime,appProperties",
            body = body,
            contentType = "multipart/related; boundary=$boundary",
            // files.create has no idempotency key. Higher layers recover by re-listing the stable logical key.
            safeToRetry = false,
        )
        decodeFile(parseJson(response.bytes, "Drive create response"))
    }

    override suspend fun update(
        fileId: String,
        name: String,
        mimeType: String,
        appProperties: Map<String, String>,
        bytes: ByteArray,
    ): DriveAppDataFile = withContext(Dispatchers.IO) {
        val boundary = "dice_thrower_${UUID.randomUUID()}"
        val body = multipartBody(boundary, fileMetadataJson(name, mimeType, appProperties), mimeType, bytes)
        val response = execute(
            method = "PATCH",
            url = "$UPLOAD_FILES_URL/${path(fileId)}?uploadType=multipart&fields=id,name,mimeType,modifiedTime,appProperties",
            body = body,
            contentType = "multipart/related; boundary=$boundary",
            safeToRetry = true,
        )
        decodeFile(parseJson(response.bytes, "Drive update response"))
    }

    override suspend fun delete(fileId: String) = withContext(Dispatchers.IO) {
        execute(
            method = "DELETE",
            url = "$FILES_URL/${path(fileId)}",
            safeToRetry = true,
        )
        Unit
    }

    private suspend fun execute(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
        safeToRetry: Boolean,
    ): HttpResponse {
        var lastFailure: CloudRepositoryException? = null
        repeat(DriveRetryPolicy.MAX_ATTEMPTS) { attempt ->
            try {
                val token = tokenProvider.accessToken()
                val response = performRequest(method, url, token, body, contentType)
                if (response.statusCode in 200..299) return response
                val failure = DriveHttpErrorPolicy.toException(
                    response.statusCode,
                    response.bytes.toString(Charsets.UTF_8),
                )
                if (failure is CloudTransientException && safeToRetry && attempt < DriveRetryPolicy.MAX_ATTEMPTS - 1) {
                    lastFailure = failure
                    sleeper(DriveRetryPolicy.delayMillis(attempt, jitterMillis()))
                } else {
                    throw failure
                }
            } catch (error: IOException) {
                val failure = CloudTransientException("Temporary Google Drive network failure", cause = error)
                if (safeToRetry && attempt < DriveRetryPolicy.MAX_ATTEMPTS - 1) {
                    lastFailure = failure
                    sleeper(DriveRetryPolicy.delayMillis(attempt, jitterMillis()))
                } else {
                    throw failure
                }
            }
        }
        throw lastFailure ?: CloudTransientException("Google Drive request failed after retries")
    }

    private fun performRequest(
        method: String,
        url: String,
        token: String,
        body: ByteArray?,
        contentType: String?,
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                setFixedLengthStreamingMode(body.size)
            }
        }
        return try {
            body?.let { connection.outputStream.use { output -> output.write(it) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }

    private fun fileMetadataJson(
        name: String,
        mimeType: String,
        appProperties: Map<String, String>,
    ): JSONObject = JSONObject()
        .put("name", name)
        .put("mimeType", mimeType)
        .put("appProperties", JSONObject(appProperties))

    private fun multipartBody(
        boundary: String,
        metadata: JSONObject,
        mimeType: String,
        bytes: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
        output.write(metadata.toString().toByteArray(Charsets.UTF_8))
        output.write("\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        return output.toByteArray()
    }

    private fun parseJson(bytes: ByteArray, context: String): JSONObject = try {
        JSONObject(bytes.toString(Charsets.UTF_8))
    } catch (error: Exception) {
        throw CloudProtocolException("Invalid $context JSON", error)
    }

    private fun decodeFile(json: JSONObject): DriveAppDataFile {
        val properties = mutableMapOf<String, String>()
        json.optJSONObject("appProperties")?.let { objectProperties ->
            val keys = objectProperties.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                properties[key] = objectProperties.getString(key)
            }
        }
        return DriveAppDataFile(
            id = json.getString("id"),
            name = json.optString("name"),
            mimeType = json.optString("mimeType"),
            modifiedTime = json.optString("modifiedTime").takeIf(String::isNotBlank),
            appProperties = properties,
        )
    }

    private data class HttpResponse(val statusCode: Int, val bytes: ByteArray)

    private companion object {
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_FILES_URL = "https://www.googleapis.com/upload/drive/v3/files"
        fun path(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
        fun encodeQuery(parameters: Map<String, String>): String = parameters.entries.joinToString("&") { (key, value) ->
            "${path(key)}=${path(value)}"
        }
    }
}
