package dev.garden.reader.features

import dev.garden.reader.data.*
import org.jsoup.Jsoup
import java.net.URI

data class AuthorRef(val blog: String, val name: String, val avatar: String?) {
    val url: String get() = "https://www.cnblogs.com/$blog/"
    companion object {
        private val reserved = setOf("p", "u", "users", "news", "aggsite", "pick", "articles", "tags", "tag", "archive", "sitehome", "ajax", "cate", "signin", "default.aspx")
        fun validBlog(value: String?): String? = value?.takeIf {
            it.matches(Regex("[A-Za-z0-9_-]{1,100}")) && it.lowercase() !in reserved
        }
        fun fromUrl(url: String?, name: String, avatar: String?): AuthorRef? = runCatching {
            val uri = URI(requireNotNull(url))
            require(uri.scheme in listOf("https", "http") && uri.host?.lowercase() in setOf("www.cnblogs.com", "cnblogs.com") &&
                uri.userInfo == null && uri.port in listOf(-1, 80, 443) && uri.rawQuery == null && uri.rawFragment == null)
            val blog = Regex("^/([A-Za-z0-9_-]+)/?$").matchEntire(uri.rawPath)?.groupValues?.get(1)
            validBlog(blog)?.let { AuthorRef(it, name, avatar) }
        }.getOrNull()
        fun from(article: Article): AuthorRef? {
            validBlog(article.blogApp)?.let { return AuthorRef(it, article.author, article.avatar) }
            val target = CommentTarget.from(article.url)?.takeIf { !it.news } ?: return null
            return validBlog(target.path.split('/')[2])?.let { AuthorRef(it, article.author, article.avatar) }
        }
    }
}

object AuthorParser {
    fun parse(html: String, author: AuthorRef, page: Int): WebsitePage {
        val base = if (page == 1) author.url else "${author.url}?page=$page"
        val modern = WebContentParser.parse(html, base)
        if (modern.articles.isNotEmpty()) return modern
        val doc = Jsoup.parse(html, base)
        val rows = doc.select(".day, .entrylistItem, .post")
        val articles = rows.mapNotNull { row ->
            val link = row.selectFirst(".postTitle a, .entrylistPosttitle a, h2 a") ?: return@mapNotNull null
            val url = WebContentParser.imageUrl(link.attr("href"), base) ?: return@mapNotNull null
            val ref = AuthorRef.from(Article(url, "", "", "", "")) ?: return@mapNotNull null
            if (!ref.blog.equals(author.blog, true)) return@mapNotNull null
            val summary = row.selectFirst(".c_b_p_desc, .entrylistPostSummary")?.clone()?.apply { select("a, img").remove() }?.text().orEmpty().removePrefix("摘要：").trim()
            val desc = row.selectFirst(".postDesc, .entrylistItemPostDesc")?.text().orEmpty()
            fun count(css: String) = row.selectFirst(css)?.text()?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
            Article(url, link.text(), author.name, summary, Regex("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}").find(desc)?.value.orEmpty(),
                avatar = author.avatar, views = count(".post-view-count"), comments = count(".post-comment-count"), likes = count(".post-digg-count"))
        }.distinctBy { it.url }
        val next = doc.select(".pager a, #nav_next_page a").firstOrNull { it.text().contains("下一页") || it.text().trim() == ">" }
            ?.attr("href")?.let { WebContentParser.imageUrl(it, base) }?.takeIf { URI(it).host == "www.cnblogs.com" && URI(it).path.startsWith("/${author.blog}/") }
        return WebsitePage(articles, next)
    }
}

