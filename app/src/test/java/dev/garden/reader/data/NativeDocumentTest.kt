package dev.garden.reader.data

import org.junit.Assert.*
import org.junit.Test

class NativeDocumentTest {
    @Test fun preservesReadingOrderAndImages() {
        val blocks = NativeDocument.parse("<h2>标题</h2><p>前文<strong>强调</strong><img src='https://img.cnblogs.com/a.png'>后文</p><pre>a &lt; b\n  code</pre>")
        assertEquals(listOf("heading", "paragraph", "image", "paragraph", "code"), blocks.map { it.kind })
        assertEquals(2, blocks[0].level)
        assertTrue(blocks[1].spans.last().bold)
        assertEquals("https://img.cnblogs.com/a.png", blocks[2].source)
        assertTrue(blocks.last().text.contains("\n  code"))
    }
    @Test fun rejectsExecutableAndInsecureImages() {
        val blocks = NativeDocument.parse("<script>bad()</script><iframe src='x'></iframe><img src='javascript:alert(1)'><img src='http://a.com/x'><p>安全文字</p>")
        assertEquals(listOf("安全文字"), blocks.map { it.text })
    }
    @Test fun supportsListQuoteAndTable() {
        val blocks = NativeDocument.parse("<ul><li>第一项</li><li>第二项</li></ul><blockquote>引用</blockquote><table><tr><td>A</td><td>B</td></tr></table>")
        assertTrue(blocks[0].text.startsWith("•"))
        assertEquals("quote", blocks[2].kind)
        assertEquals("table", blocks[3].kind)
    }
    @Test fun websiteRetainsThumbnail() {
        val html = "<article class='post-item'><a class='post-item-title' href='/a/p/1'>图文</a><figure><img src='https://img.cnblogs.com/a.jpg'></figure></article>"
        assertEquals("https://img.cnblogs.com/a.jpg", WebContentParser.parse(html, "https://www.cnblogs.com/").articles.single().image)
    }
}
