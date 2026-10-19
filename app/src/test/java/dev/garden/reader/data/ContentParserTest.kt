package dev.garden.reader.data

import org.junit.Assert.*
import org.junit.Test

class ContentParserTest {
    @Test fun extractsNewsWithoutPageChrome() {
        val body = ContentParser.extractBody("<header>导航</header><div id='news_body'><p>新闻正文</p><script>bad()</script></div><footer>评论</footer>", "https://news.cnblogs.com/n/1/")
        assertTrue(body.contains("新闻正文")); assertFalse(body.contains("导航")); assertFalse(body.contains("<script"))
    }
    @Test fun extractsBlogBodyOnly() {
        val body = ContentParser.extractBody("<div id='cnblogs_post_body'><p>文章正文</p></div><aside>广告</aside>", "https://www.cnblogs.com/a/p/1")
        assertTrue(body.contains("文章正文")); assertFalse(body.contains("广告"))
    }
    @Test fun knowledgeUsesItsDedicatedBodyNode() {
        val body = ContentParser.extractBody("<header>知识库导航</header><div id='ArticleCnt'><p>知识正文</p></div>", "https://kb.cnblogs.com/page/42/")
        assertTrue(body.contains("知识正文")); assertFalse(body.contains("知识库导航"))
    }
    @Test fun rejectsUnsafeUrlsAndLookalikeDomains() {
        assertNull(ContentParser.safeWebUrl("javascript:alert(1)")); assertNull(ContentParser.safeWebUrl("file:///secret"))
        assertFalse(ContentParser.isCnblogsUrl("https://cnblogs.com.evil.test/a"))
        assertFalse(ContentParser.isCnblogsUrl("https://user@www.cnblogs.com/a"))
        assertTrue(ContentParser.isCnblogsUrl("https://kb.cnblogs.com/page/1/"))
    }
    @Test fun removesActiveContentAndResolvesLinks() {
        val body = ContentParser.cleanBody("<script>bad()</script><iframe src='/x'></iframe><a href='javascript:bad()'>bad</a><a href='/a/p/2'>next</a><img src='//img.cnblogs.com/a.png' onerror='bad()'>", "https://www.cnblogs.com/a/p/1")
        assertFalse(body.contains("<script")); assertFalse(body.contains("<iframe")); assertFalse(body.contains("onerror"))
        assertFalse(body.contains("javascript:")); assertTrue(body.contains("https://www.cnblogs.com/a/p/2"))
    }
    @Test fun lazyImagesUseOriginalHttpsSource() {
        val body = ContentParser.cleanBody("<img src='/placeholder.png' data-original='http://img.cnblogs.com/real.png'>", "https://www.cnblogs.com/a/p/1")
        assertTrue(body.contains("https://img.cnblogs.com/real.png")); assertFalse(body.contains("placeholder"))
    }
    @Test fun preservesNativeCodeAndTableInput() {
        val body = ContentParser.cleanBody("<pre><code>val x = 1</code></pre><table><tr><td>A</td></tr></table>", "https://www.cnblogs.com/a/p/1")
        assertTrue(body.contains("<pre><code>")); assertTrue(body.contains("<table>"))
    }
    @Test(expected = IllegalStateException::class) fun missingArticleBodyFailsRatherThanRenderingNavigation() {
        ContentParser.extractBody("<nav id='content'>登录后继续</nav>", "https://www.cnblogs.com/a")
    }
}
