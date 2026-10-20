package dev.garden.reader.features

import dev.garden.reader.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URI

data class AuthorRef(val blog: String, val name: String, val avatar: String?) {
    val url: String get() = "https://www.cnblogs.com/$blog/"
    companion object {
        fun from(article: Article): AuthorRef? {
            val target = CommentTarget.from(article.url)?.takeIf { !it.news } ?: return null
            return AuthorRef(target.path.split('/')[2], article.author, article.avatar)
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

class Authors {
    suspend fun page(author: AuthorRef, number: Int): WebsitePage = withContext(Dispatchers.IO) {
        val url = if (number == 1) author.url else "${author.url}?page=$number"
        val (status, body) = PublicHttp.get(url, "www.cnblogs.com", 4 * 1024 * 1024)
        check(status == 200) { "作者页读取失败（HTTP $status）" }
        val parsed = AuthorParser.parse(body, author, number)
        check(parsed.articles.isNotEmpty()) { "作者暂无公开文章，或页面结构暂不支持" }
        parsed
    }
}
