package dev.garden.reader.features

import dev.garden.reader.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.DayOfWeek

class ReaderFeaturesTest {
    private val blog = Article("https://www.cnblogs.com/alice/p/123", "Kotlin 原生代码", "Alice", "Compose", "")
    private val news = Article("https://news.cnblogs.com/n/456/", "科技新闻", "新闻编辑", "Kotlin 新闻", "")
    @Test fun authorUsesValidatedBlogName() {
        assertEquals(AuthorRef("alice", "Alice", null), AuthorRef.from(blog))
        assertNull(AuthorRef.from(news)); assertNull(AuthorRef.from(blog.copy(url = "https://www.cnblogs.com.evil.org/alice/p/123")))
    }
    @Test fun parsesAuthorWebsitePosts() {
        val html = """<div class="day"><div class="postTitle"><a href="/alice/p/123">文章</a></div><div class="c_b_p_desc">摘要：正文<a>阅读全文</a></div><div class="postDesc">2026-10-20 12:34<span class="post-view-count">阅读(12)</span><span class="post-comment-count">评论(2)</span><span class="post-digg-count">推荐(3)</span></div></div>"""
        val article = AuthorParser.parse(html, AuthorRef("alice", "Alice", null), 1).articles.single()
        assertEquals("正文", article.summary); assertEquals(12, article.views); assertEquals(2, article.comments); assertEquals(3, article.likes)
    }
    @Test fun authorCannotAdoptAnotherBlogPosts() {
        assertTrue(AuthorParser.parse("""<div class="day"><div class="postTitle"><a href="https://www.cnblogs.com/bob/p/123">wrong</a></div></div>""", AuthorRef("alice", "Alice", null), 1).articles.isEmpty())
    }
    @Test fun collectionContainsBothNewsAndBlogs() {
        val items = listOf(blog, news)
        assertEquals(2, BookmarkSearch.filter(items, "Kotlin").size)
        assertEquals(listOf(news), BookmarkSearch.filter(items, "Kotlin", 2))
        assertEquals(listOf(blog), BookmarkSearch.filter(items, "", 1))
    }
    @Test fun titleFilterAndCaseInsensitiveQuery() {
        assertEquals(listOf(blog), BookmarkSearch.filter(listOf(blog, news), "kotlin", titleOnly = true))
        assertEquals(listOf(blog), BookmarkSearch.filter(listOf(blog, news), "alice"))
    }
    @Test fun countsOnlyActiveMonotonicTime() {
        assertEquals(1000L, ReadingMath.delta(100, 1100, true))
        assertEquals(0L, ReadingMath.delta(100, 1100, false))
        assertEquals(0L, ReadingMath.delta(100, 10_000, true))
        assertEquals(0L, ReadingMath.delta(100, 20, true))
    }
    @Test fun heatmapHasWholeWeeksAcrossYearBoundary() {
        val dates = ReadingMath.dates(LocalDate.of(2026, 1, 2))
        assertEquals(182, dates.size); assertEquals(DayOfWeek.MONDAY, dates.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, dates.last().dayOfWeek); assertTrue(dates.contains(LocalDate.of(2026, 1, 2)))
    }
    @Test fun heatmapIntensityThresholds() {
        assertEquals(0, ReadingMath.level(0)); assertEquals(1, ReadingMath.level(1))
        assertEquals(2, ReadingMath.level(300_000)); assertEquals(4, ReadingMath.level(1_800_000))
    }
    @Test fun codeLanguageSurvivesSanitizingAndTextIsUnchanged() {
        val html = ContentParser.cleanBody("<pre><code class=language-kotlin>val x = 1\nprintln(x)</code></pre>", blog.url)
        val block = NativeDocument.parse(html).single(); assertEquals("kotlin", block.language)
        assertEquals("val x = 1\nprintln(x)", block.text)
    }
    @Test fun syntaxRecognizesStringsAndCommentsWithoutExecuting() {
        val code = "val text = \"class\" // return\nclass Test"
        val tokens = CodeTokens.parse(code)
        assertEquals("keyword", tokens.first().kind)
        assertTrue(tokens.any { it.kind == "string" && code.substring(it.start, it.end) == "\"class\"" })
        assertTrue(tokens.any { it.kind == "comment" }); assertTrue(tokens.zipWithNext().all { it.first.end <= it.second.start })
    }
    @Test fun publicCommentsExtractOtherAuthors() {
        val html = """<div class="feedbackItem"><div class="feedbackListSubtitle"><a id="a_comment_author_12">其他用户</a><span class="comment_date">2026-10-20</span></div><div id="comment_body_12" class="blog_comment_body">其他人的评论<script>bad()</script></div></div>"""
        val comment = PublicCommentParser.parse(html, blog.url).single()
        assertEquals("其他用户", comment.author); assertEquals("12", comment.id); assertEquals("其他人的评论", comment.body)
    }
    @Test fun navigationAndFormsAreNotComments() {
        assertTrue(PublicCommentParser.parse("<form>发布评论</form><nav>下一页</nav>", blog.url).isEmpty())
    }
}
