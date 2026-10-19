package dev.garden.reader.account

import java.net.URI

object NativeAuthPolicy {
    fun validToken(value: String): Boolean = value.length == 64 && value.all {
        it.isLetterOrDigit() && it.code < 128 || it in "_-+/="
    }
    fun validIdentity(id: String, name: String): Boolean =
        id.isNotBlank() && id != "null" && name.isNotBlank() && name != "null"

    fun articlePath(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return null
        when (uri.host?.lowercase()) {
            "news.cnblogs.com" -> Regex("^/n/(\\d+)/?$").find(uri.path)?.groupValues?.get(1)?.let { "/newsitems/$it/body" }
            "www.cnblogs.com", "cnblogs.com" -> (Regex("/p/(\\d+)(?:\\.html|/[^?#]*)?/?$").find(uri.path)
                ?: Regex("/(\\d+)\\.html$").find(uri.path))?.groupValues?.get(1)?.let { "/blogposts/$it/body" }
            "kb.cnblogs.com" -> Regex("^/page/(\\d+)/?$").find(uri.path)?.groupValues?.get(1)?.let { "/kbarticles/$it/body" }
            else -> null
        }
    }.getOrNull()
}
