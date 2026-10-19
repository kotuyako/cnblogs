package dev.garden.reader.features

import java.net.HttpURLConnection
import java.net.URI

/** Public requests only. No cookies or account credentials; all responses are size bounded. */
object PublicHttp {
    fun get(url: String, allowedHost: String, limit: Int = 2 * 1024 * 1024): Pair<Int, String> {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == allowedHost && uri.userInfo == null && uri.port in listOf(-1, 443))
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "CnblogsNative/1.4.0 (unofficial)")
            connection.setRequestProperty("Accept", if (allowedHost == "api.github.com") "application/vnd.github+json" else "text/html")
            val status = connection.responseCode
            if (status !in 200..299) return status to ""
            return status to connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val bytes = ByteArray(8192)
                while (true) { val n = input.read(bytes); if (n < 0) break
                    check(out.size() + n <= limit) { "响应过大" }; out.write(bytes, 0, n) }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
