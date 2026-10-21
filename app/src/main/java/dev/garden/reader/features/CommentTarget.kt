package dev.garden.reader.features

import java.net.URI
import dev.garden.reader.data.Article

data class CommentTarget(val path: String, val news: Boolean) {
    companion object {
        fun from(article: Article): CommentTarget? {
            val legacy = from(article.url) ?: return null
            if (legacy.news) return legacy
            val blog = AuthorRef.validBlog(article.blogApp) ?: legacy.path.split('/')[2]
            return CommentTarget("/blogs/$blog/posts/${legacy.path.split('/')[4]}/comments", false)
        }
        fun from(url: String): CommentTarget? = runCatching {
            val u = URI(url)
            if (u.scheme != "https" || u.userInfo != null || u.port !in listOf(-1, 443)) return null
            if (u.host == "news.cnblogs.com") {
                val id = Regex("^/n/(\\d+)/?$").matchEntire(u.path)?.groupValues?.get(1) ?: return null
                CommentTarget("/news/$id/comments", true)
            } else if (u.host in listOf("www.cnblogs.com", "cnblogs.com")) {
                val segments = u.path.trim('/').split('/')
                val blog = segments.firstOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return null
                val id = (Regex("/p/(\\d+)(?:\\.html|/[^?#]*)?/?$").find(u.path)
                    ?: Regex("/(\\d+)\\.html$").find(u.path))?.groupValues?.get(1) ?: return null
                CommentTarget("/blogs/$blog/posts/$id/comments", false)
            } else null
        }.getOrNull()
    }
}
