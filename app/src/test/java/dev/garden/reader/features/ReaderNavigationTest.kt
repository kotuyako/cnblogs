package dev.garden.reader.features

import dev.garden.reader.data.ContentParser
import dev.garden.reader.data.NativeDocument
import org.junit.Assert.*
import org.junit.Test

class ReaderNavigationTest {
    private val url = "https://www.cnblogs.com/cmt/p/123.html"
    @Test fun sanitizerPreservesNativeAnchorLinks() {
        val clean = ContentParser.cleanBody("<ul><li><a href='#section-2'>第二节</a></li></ul><h2 id='section-2'>第二节</h2><p>正文</p>", url)
        val blocks = NativeDocument.parse(clean)
        assertEquals("#section-2", blocks.first().spans.first { it.link != null }.link)
        assertEquals(2, ReaderNavigation.target("#section-2", url, blocks))
    }
    @Test fun encodedAnchorAndSamePageAbsoluteUrlResolve() {
        val blocks = NativeDocument.parse("<h2 id='章节+二'>第二节</h2>")
        assertEquals(1, ReaderNavigation.target("#%E7%AB%A0%E8%8A%82+%E4%BA%8C", url, blocks))
        assertEquals(1, ReaderNavigation.target("$url#章节+二", url, blocks))
    }
    @Test fun refusesForeignArticleAndUnknownAnchor() {
        val blocks = NativeDocument.parse("<h2 id='section'>标题</h2>")
        assertNull(ReaderNavigation.target("https://evil.example/a#section", url, blocks))
        assertNull(ReaderNavigation.target("https://www.cnblogs.com/cmt/p/other.html#section", url, blocks))
        assertNull(ReaderNavigation.target("#missing", url, blocks))
        assertNull(ReaderNavigation.target("javascript:alert(1)", url, blocks))
    }
    @Test fun relativeNonAnchorLinksKeepTheirOriginalDestination() {
        val clean = ContentParser.cleanBody("<p><a href='/other/p/456.html'>另一篇</a><a href='../next.html'>相对链接</a></p>", url)
        val links = NativeDocument.parse(clean).flatMap { it.spans }.mapNotNull { it.link }
        assertEquals(listOf("https://www.cnblogs.com/other/p/456.html", "https://www.cnblogs.com/cmt/next.html"), links)
    }
    @Test fun supportsNestedNamedHeadingAnchors() {
        val blocks = NativeDocument.parse("<p>前文</p><h3><a name='legacy'></a><strong>章节</strong></h3>")
        assertEquals("legacy", blocks[1].anchor)
        assertEquals(2, ReaderNavigation.target("#legacy", url, blocks))
        assertEquals(6, ReaderNavigation.blockItem(5))
    }
    @Test fun scrollDirectionHasHysteresisAndNoItemIndexJumps() {
        val policy = ScrollChrome(18f)
        assertNull(policy.update(-6f)); assertNull(policy.update(-6f))
        assertEquals(false, policy.update(-7f))
        assertNull(policy.update(7f)); assertNull(policy.update(-1f))
        assertNull(policy.update(8f)); assertEquals(true, policy.update(11f))
        assertNull(policy.update(Float.NaN)); assertNull(policy.update(0f))
    }
}
