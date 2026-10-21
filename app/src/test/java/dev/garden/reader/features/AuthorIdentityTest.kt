package dev.garden.reader.features

import dev.garden.reader.data.Article
import org.junit.Assert.*
import org.junit.Test

class AuthorIdentityTest {
    @Test fun explicitBlogAppPrecedesArticleUrlAndNeverUsesNickname() {
        val article = Article("https://www.cnblogs.com/old/p/12.html", "", "中文昵称", "", "", blogApp = "real-blog")
        assertEquals("real-blog", AuthorRef.from(article)?.blog)
        assertNull(AuthorRef.from(Article("https://news.cnblogs.com/n/12/", "", "real-blog", "", "")))
    }
    @Test fun reliableCommentHomeOnly() {
        assertEquals("example", AuthorRef.fromUrl("https://www.cnblogs.com/example/", "昵称", null)?.blog)
        assertEquals("example", AuthorRef.fromUrl("http://cnblogs.com/example", "昵称", null)?.blog)
        for (url in listOf("https://space.cnblogs.com/u/example/", "https://www.cnblogs.com.evil/example/",
            "https://user@www.cnblogs.com/example/", "https://www.cnblogs.com/%65xample/", "https://www.cnblogs.com/p/",
            "javascript:alert(1)", "https://www.cnblogs.com/example/?redirect=other")) assertNull(url, AuthorRef.fromUrl(url, "昵称", null))
    }
    @Test fun publicCommentRetainsReliableAuthorUrl() {
        val html = """<div class="feedbackItem"><a id="a_comment_author_1" href="https://www.cnblogs.com/example/">昵称</a><div id="comment_body_1" class="blog_comment_body">正文</div></div>"""
        assertEquals("example", PublicCommentParser.parse(html, "https://www.cnblogs.com/").single().authorRef()?.blog)
    }
}
