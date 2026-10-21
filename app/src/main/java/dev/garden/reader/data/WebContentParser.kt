package dev.garden.reader.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

data class WebsitePage(val articles: List<Article>, val next: String?)

/** Public website content only; advertisements, navigation, scripts and RSS are not a data source. */
object WebContentParser {
    fun imageUrl(value: String, base: String): String? = runCatching {
        ContentParser.safeWebUrl(URI(base).resolve(value.trim()).toString())
            ?.replaceFirst("http://", "https://")
    }.getOrNull()?.takeIf { value.isNotBlank() }

    fun parse(html: String, url: String): WebsitePage {
        val doc = Jsoup.parse(html, url)
        fun image(node: Element?): String? = node?.let {
            imageUrl(it.attr("data-original").ifBlank { it.attr("data-src") }.ifBlank { it.attr("src") }, url)
        }
        fun count(raw: String): Int? = Regex("\\d+").find(raw.replace(",", ""))?.value?.toIntOrNull()
        val rows = doc.select("article.post-item, .post_item, .news_block, .aiticle_item")
        val articles = rows.mapNotNull { row ->
            val title = row.selectFirst("a.post-item-title, a.titlelnk, .news_entry a, .msg_title a") ?: return@mapNotNull null
            val link = imageUrl(title.attr("href"), url)?.takeIf(ContentParser::isCnblogsUrl) ?: return@mapNotNull null
            if (title.text().isBlank()) return@mapNotNull null
            val footer = row.selectFirst(".post-item-foot, .post_item_foot, .entry_footer, .msg_tag")
            val author = row.selectFirst(".post-item-author, .lightblue, .entry_footer > a")?.text()
                ?.takeIf { it.isNotBlank() } ?: if (URI(url).host == "kb.cnblogs.com") "博客园知识库" else "博客园"
            val published = Regex("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}").find(footer?.text().orEmpty())?.value.orEmpty()
            val summary = row.selectFirst(".post-item-summary, .post_item_summary, .entry_summary, .msg_summary")?.clone()
                ?.apply { select("img, script, style").remove() }?.text().orEmpty()
            val views = row.selectFirst("[title^=阅读], .article_view, .entry_footer .view, .msg_tag .view")
            val likes = row.selectFirst("[title^=推荐], .diggnum, .digg count, .recommend")
            val comments = row.selectFirst("[title^=评论], .article_comment, .entry_footer .comment")
            Article(link, title.text(), author, summary.take(1000), published,
                image(row.selectFirst("figure img, .post_item_summary img:not(.pfs), .entry_summary img:not(.topic_img)")),
                image(row.selectFirst("img.avatar, img.pfs")),
                count(views?.attr("title").orEmpty().ifBlank { views?.text().orEmpty() }),
                count(likes?.attr("title").orEmpty().ifBlank { likes?.text().orEmpty() }),
                count(comments?.attr("title").orEmpty().ifBlank { comments?.text().orEmpty() }),
                blogApp = dev.garden.reader.features.AuthorRef.fromUrl(
                    row.selectFirst(".post-item-author, .lightblue")?.absUrl("href"), author, null)?.blog)
        }.distinctBy { it.url }.take(100)
        val next = doc.select(".pager a, .paginator a, .pagination a, a[rel=next]").firstOrNull {
            it.attr("rel") == "next" || it.text().trim() in setOf(">", "下一页", "下一页 >", "Next")
        }?.let { imageUrl(it.attr("href"), url) }?.takeIf {
            val current = URI(url); val target = URI(it)
            target.host == current.host && target.scheme == "https" && target.userInfo == null && it != url
        }
        return WebsitePage(articles, next)
    }
}
