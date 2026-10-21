package dev.garden.reader.features

import dev.garden.reader.data.WebContentParser
import org.jsoup.Jsoup

data class NativeComment(val id: String, val author: String, val date: String, val body: String, val avatar: String?, val authorUrl: String? = null) {
    fun authorRef(): AuthorRef? = AuthorRef.fromUrl(authorUrl, author, avatar)
}
data class CommentPage(val items: List<NativeComment>, val hasMore: Boolean, val source: String)

object PublicCommentParser {
    fun parse(html: String, base: String): List<NativeComment> {
        val doc = Jsoup.parse(html, base)
        return doc.select(".feedbackItem, .user_comment, .comment-item").mapNotNull { row ->
            val body = row.selectFirst(".blog_comment_body, .comment_body, .comment-content, .comment_content") ?: return@mapNotNull null
            val content = body.clone().apply { select("script, style, iframe").remove() }.text()
            if (content.isBlank()) return@mapNotNull null
            val id = Regex("\\d+").find(body.id().ifBlank { row.id() })?.value
                ?: row.attr("data-comment-id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val author = row.selectFirst("a.comment_author, .comment_author a, .comment_info a, .feedbackListSubtitle a[id*=author], a[id^=a_comment_author_]")
            val name = author?.text()?.takeIf { it.isNotBlank() } ?: row.selectFirst(".comment_author")?.text().orEmpty()
            NativeComment(id, name.ifBlank { "博客园用户" }, row.selectFirst(".comment_date, .comment_time, .comment-time")?.text().orEmpty(),
                content, row.selectFirst("img.avatar, img.comment_avatar, .comment_avatar img, .feedbackItem img")?.attr("src")
                    ?.let { WebContentParser.imageUrl(it, base) },
                author?.attr("href")?.let { WebContentParser.imageUrl(it, base) })
        }.distinctBy { it.id }
    }
}
