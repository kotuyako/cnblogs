package dev.garden.reader.features

import java.io.IOException
import org.json.JSONObject
import org.jsoup.Jsoup

/** The website can return HTTP 200 while withholding all comments behind a login gate. */
class PublicCommentFailure(message: String) : IOException(message)

object PublicCommentProtocol {
    const val BLOG_LOGIN_REQUIRED = "博客园网页返回 HTTP 200，但明确要求网页登录后才能查看评论。APP 的 PAT 授权不等于网页会话，未读取任何受限评论。"
    const val NEWS_LOGIN_REQUIRED = "博客园新闻网页明确要求网页登录后才能查看评论。APP 的 PAT 授权不等于网页会话，未读取任何受限评论。"

    fun blogUrl(target: CommentTarget, page: Int): String {
        require(page > 0 && !target.news)
        val match = Regex("^/blogs/([A-Za-z0-9_-]+)/posts/(\\d+)/comments$").matchEntire(target.path)
            ?: throw IllegalArgumentException("无法可靠识别公开评论的文章地址")
        val (blog, id) = match.destructured
        // Keep commentForm enabled: otherwise the server conceals its explicit login requirement.
        return "https://www.cnblogs.com/$blog/ajax/comments-block?postId=$id&pageIndex=$page" +
            "&anchorCommentId=0&isDesc=false&order=0&loadCommentBox=true"
    }

    fun newsId(target: CommentTarget): String {
        require(target.news)
        return Regex("^/news/(\\d+)/comments$").matchEntire(target.path)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("无法可靠识别新闻评论的文章地址")
    }

    fun blog(raw: String, base: String, page: Int): CommentPage {
        require(page > 0)
        val response = runCatching { JSONObject(raw) }.getOrElse {
            throw PublicCommentFailure("博客园网页评论响应不是预期的 JSON；不能视为暂无评论。")
        }
        val form = response.opt("commentForm") as? String ?: ""
        if (requiresWebLogin(form)) throw PublicCommentFailure(BLOG_LOGIN_REQUIRED)
        val html = response.opt("comments") as? String
            ?: throw PublicCommentFailure("博客园网页未返回 comments 字段；不能视为暂无评论。")
        val count = nonNegativeCount(response.opt("commentCount"))
            ?: throw PublicCommentFailure("博客园网页未返回有效评论数量；不能视为暂无评论。")
        val items = PublicCommentParser.parse(html, base)
        if (items.isEmpty()) {
            if (count == 0 && html.isBlank()) return CommentPage(emptyList(), false, "博客园网页公开评论")
            throw PublicCommentFailure("博客园网页显示有 $count 条评论，但未返回可读取的评论正文；可能需要网页会话或页面结构已变化。")
        }
        return CommentPage(items, hasNextPage(html, page), "博客园网页公开评论")
    }

    fun news(raw: String, base: String, permissionHtml: String? = null): CommentPage {
        if (requiresWebLogin(raw) || permissionHtml?.let(::requiresWebLogin) == true) {
            throw PublicCommentFailure(NEWS_LOGIN_REQUIRED)
        }
        val items = PublicCommentParser.parse(raw, base)
        if (items.isEmpty()) {
            // GetComments returns a bare newline for anonymous readers, even when comments exist.
            throw PublicCommentFailure("新闻网页未返回可读取的评论正文；HTTP 200 空片段不能证明暂无评论。")
        }
        // The website's GetNewsComment loads the complete returned fragment, without page arguments.
        return CommentPage(items, false, "博客园新闻网页公开评论")
    }

    fun requiresWebLogin(html: String): Boolean {
        val text = Jsoup.parse(html).text().replace(Regex("\\s+"), "")
        return text.contains("登录后才能查看") || text.contains("查看或发表评论，请先登录") ||
            text.contains("查看评论，请先登录")
    }

    private fun nonNegativeCount(value: Any?): Int? {
        if (value !is Number) return null
        val number = value.toDouble()
        return number.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
    }

    private fun hasNextPage(html: String, page: Int): Boolean {
        val doc = Jsoup.parse(html)
        return doc.select(".pager a, #comments_pager_top a, #comments_pager_bottom a").any { link ->
            val requested = Regex("renderComments\\(\\s*(\\d+)").find(link.attr("onclick"))
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("(?:[?&]|#)pageIndex=(\\d+)").find(link.attr("href"))
                    ?.groupValues?.get(1)?.toIntOrNull()
            requested != null && requested > page
        }
    }
}
