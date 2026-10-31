package dev.garden.reader

import dev.garden.reader.features.CommentTarget
import dev.garden.reader.features.PublicCommentFailure
import dev.garden.reader.features.PublicCommentProtocol
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PublicCommentProtocolTest {
    private val base = "https://www.cnblogs.com/cmt/p/19316348"

    // Actual anonymous public response from 2026-10-31. No account credentials were sent.
    // https://www.cnblogs.com/cmt/ajax/comments-block?postId=19316348&isDesc=false&order=0&loadCommentBox=true
    private val actualLoginGate = """{"commentCount":45,"comments":"","commentForm":"\u003Cdiv class=\u0022login_tips\u0022\u003E\n    登录后才能查看或发表评论，立即 \u003Ca rel=\u0022nofollow\u0022 href=\u0022javascript:void(0);\u0022 class=\u0022underline\u0022 onclick=\u0022return account.login(\u0027!comments\u0027);\u0022\u003E登录\u003C/a\u003E 或者\n    \u003Ca href=\u0022https://www.cnblogs.com/\u0022\u003E逛逛\u003C/a\u003E 博客园首页\n\u003C/div\u003E\n"}"""

    private fun failure(block: () -> Unit): String {
        try { block(); fail("Expected a visible protocol error, not an empty list") }
        catch (error: PublicCommentFailure) { return error.message.orEmpty() }
        return ""
    }

    @Test fun actualHttp200LoginGateIsNotEmptyComments() {
        assertEquals(PublicCommentProtocol.BLOG_LOGIN_REQUIRED,
            failure { PublicCommentProtocol.blog(actualLoginGate, base, 1) })
    }

    @Test fun loginGateRemainsAuthErrorEvenIfCountIsZero() {
        val zero = JSONObject(actualLoginGate).put("commentCount", 0).toString()
        assertEquals(PublicCommentProtocol.BLOG_LOGIN_REQUIRED,
            failure { PublicCommentProtocol.blog(zero, base, 1) })
    }

    @Test fun onlyExplicitUngatedZeroCanBecomeEmpty() {
        val page = PublicCommentProtocol.blog("""{"commentCount":0,"comments":"","commentForm":""}""", base, 1)
        assertTrue(page.items.isEmpty()); assertFalse(page.hasMore)
        assertTrue(failure { PublicCommentProtocol.blog("""{"commentCount":45,"comments":"","commentForm":""}""", base, 1) }.contains("45"))
    }

    @Test fun absentSchemaAndFractionalCountsAreNotEmpty() {
        assertTrue(failure { PublicCommentProtocol.blog("<html>访问受限</html>", base, 1) }.contains("JSON"))
        assertTrue(failure { PublicCommentProtocol.blog("""{"title":"Unauthorized","status":401}""", base, 1) }.contains("comments"))
        assertTrue(failure { PublicCommentProtocol.blog("""{"commentCount":0.5,"comments":""}""", base, 1) }.contains("有效评论数量"))
    }

    @Test fun newsNewlineIsNeverAssumedToMeanZero() {
        assertTrue(failure { PublicCommentProtocol.news("\n", "https://news.cnblogs.com/n/841663/") }.contains("不能证明暂无评论"))
        // Actual anonymous response of /NewsAjax/LoadDetailommentInfo?newsId=843338 on 2026-10-31.
        val login = """查看或发表评论，请先<a onclick="login('bottom'); return false " href="javascript: void(0); ">登录</a>"""
        assertEquals(PublicCommentProtocol.NEWS_LOGIN_REQUIRED,
            failure { PublicCommentProtocol.news("\n", "https://news.cnblogs.com/n/843338/", login) })
    }

    // Synthetic successful HTML tests the native parser only; not evidence of anonymous live access.
    private fun row(id: String) = """<div class="feedbackItem"><div class="feedbackListSubtitle"><a id="a_comment_author_$id" href="https://www.cnblogs.com/other-blog/">其他用户</a><span class="comment_date">2026-10-31 10:30</span></div><div id="comment_body_$id" class="blog_comment_body">公开正文<script>bad()</script></div></div>"""

    @Test fun legalReturnedCommentsKeepAuthorLinkAndSanitizeBody() {
        val response = JSONObject().put("commentCount", 1).put("comments", row("123")).put("commentForm", "").toString()
        val page = PublicCommentProtocol.blog(response, base, 1)
        val comment = page.items.single()
        assertEquals("123", comment.id); assertEquals("公开正文", comment.body)
        assertEquals("其他用户", comment.author); assertEquals("other-blog", comment.authorRef()?.blog)
        assertFalse(page.hasMore)
    }

    @Test fun paginationNeedsExplicitServerNextPageNotAnInventedPageSize() {
        val html = row("123") + """<div id="comments_pager_bottom" class="pager"><a onclick="commentManager.renderComments(2,50);return false;">下一页</a></div>"""
        val response = JSONObject().put("commentCount", 51).put("comments", html).toString()
        assertTrue(PublicCommentProtocol.blog(response, base, 1).hasMore)
        assertFalse(PublicCommentProtocol.blog(response, base, 2).hasMore)
    }

    @Test fun exactPublicUrlIncludesPageAndVisibleGate() {
        val url = PublicCommentProtocol.blogUrl(CommentTarget("/blogs/cmt/posts/19316348/comments", false), 2)
        assertEquals("https://www.cnblogs.com/cmt/ajax/comments-block?postId=19316348&pageIndex=2&anchorCommentId=0&isDesc=false&order=0&loadCommentBox=true", url)
        assertFalse(url.contains("token", ignoreCase = true)); assertFalse(url.contains("cookie", ignoreCase = true))
        assertEquals("843338", PublicCommentProtocol.newsId(CommentTarget("/news/843338/comments", true)))
    }
}
