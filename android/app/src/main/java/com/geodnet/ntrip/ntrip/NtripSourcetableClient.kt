package com.geodnet.ntrip.ntrip

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

object NtripSourcetableClient {

    private const val USER_AGENT = "ntrip client Android/0.1.0"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000

    suspend fun fetch(
        host: String,
        port: Int,
        username: String = "",
        password: String = ""
    ): Result<NtripSourcetable> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS

                val output = socket.getOutputStream()
                val reqBuilder = StringBuilder()
                reqBuilder.append("GET / HTTP/1.0\r\n")
                reqBuilder.append("User-Agent: $USER_AGENT\r\n")
                reqBuilder.append("Accept: */*\r\n")
                reqBuilder.append("Connection: close\r\n")

                if (username.isNotBlank() || password.isNotBlank()) {
                    val auth = "$username:$password"
                    val b64 = Base64.encodeToString(auth.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                    reqBuilder.append("Authorization: Basic $b64\r\n")
                }
                reqBuilder.append("\r\n")

                output.write(reqBuilder.toString().toByteArray(StandardCharsets.US_ASCII))
                output.flush()

                val input = socket.getInputStream()
                val responseBytes = readFully(input)
                val responseText = String(responseBytes, StandardCharsets.UTF_8)

                if (responseText.isEmpty()) {
                    return@withContext Result.failure(Exception("Empty response from caster $host:$port"))
                }

                // Check for HTTP or SOURCETABLE response header
                val headerEnd = responseText.indexOf("\r\n\r\n")
                val body = if (headerEnd >= 0) {
                    responseText.substring(headerEnd + 4)
                } else {
                    responseText
                }

                val sourcetable = NtripSourcetable.parse(body)
                if (sourcetable.streams.isEmpty() && !body.contains("SOURCETABLE", ignoreCase = true) && !body.contains("STR;", ignoreCase = true)) {
                    val firstLine = responseText.lines().firstOrNull() ?: ""
                    return@withContext Result.failure(Exception("Caster returned non-sourcetable response: $firstLine"))
                }

                Result.success(sourcetable)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readFully(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var n: Int
        try {
            while (input.read(buffer).also { n = it } != -1) {
                out.write(buffer, 0, n)
            }
        } catch (_: java.net.SocketTimeoutException) {
            // Finished or timeout -- return whatever we have read so far
        } catch (_: java.io.IOException) {
        }
        return out.toByteArray()
    }
}
