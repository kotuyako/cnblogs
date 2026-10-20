package dev.garden.reader.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class NativeSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false,
    val code: Boolean = false, val link: String? = null)
data class NativeBlock(val kind: String, val spans: List<NativeSpan> = emptyList(),
    val level: Int = 0, val source: String? = null, val caption: String = "", val language: String = "") {
    val text: String get() = spans.joinToString("") { it.text }
}

/** HTML is only a transport format. No browser, script engine or Android web widget is used. */
object NativeDocument {
    fun parse(html: String): List<NativeBlock> {
        val result = mutableListOf<NativeBlock>()
        val pending = mutableListOf<NativeSpan>()
        fun flush(kind: String = "paragraph", level: Int = 0) {
            if (pending.any { it.text.isNotBlank() }) result += NativeBlock(kind, pending.toList(), level)
            pending.clear()
        }
        fun walk(node: Node, bold: Boolean = false, italic: Boolean = false, code: Boolean = false, link: String? = null) {
            if (node is TextNode) {
                pending += NativeSpan(node.text(), bold, italic, code, link); return
            }
            if (node !is Element) return
            val tag = node.normalName()
            when {
                tag in setOf("script", "style", "iframe", "video", "audio", "form") -> return
                tag == "img" -> {
                    flush()
                    ContentParser.safeWebUrl(node.attr("src"))?.takeIf { it.startsWith("https://") }?.let {
                        result += NativeBlock("image", source = it, caption = node.attr("alt"))
                    }
                }
                tag == "pre" -> {
                    val classes = (node.classNames() + node.selectFirst("code")?.classNames().orEmpty())
                    val language = classes.firstOrNull { it.startsWith("language-") || it.startsWith("lang-") }
                        ?.substringAfter('-')?.take(30).orEmpty()
                    flush(); result += NativeBlock("code", listOf(NativeSpan(node.wholeText(), code = true)), language = language)
                }
                tag == "table" -> {
                    flush()
                    node.select("tr").forEach { row -> result += NativeBlock("table",
                        listOf(NativeSpan(row.children().joinToString("    │    ") { it.text() }))) }
                }
                tag == "hr" -> { flush(); result += NativeBlock("rule") }
                tag == "br" -> pending.add(NativeSpan("\n"))
                else -> {
                    val heading = tag.matches(Regex("h[1-6]"))
                    val block = heading || tag in setOf("p", "div", "section", "article", "ul", "ol", "li", "blockquote", "figure", "figcaption")
                    if (block) flush()
                    if (tag == "li") pending += NativeSpan("•  ")
                    val nextLink = if (tag == "a") ContentParser.safeWebUrl(node.attr("href")) else link
                    node.childNodes().forEach { walk(it, bold || tag in setOf("b", "strong"),
                        italic || tag in setOf("i", "em"), code || tag == "code", nextLink) }
                    if (block) flush(if (heading) "heading" else if (tag == "blockquote") "quote" else "paragraph",
                        if (heading) tag.last().digitToInt() else 0)
                }
            }
        }
        Jsoup.parseBodyFragment(html).body().childNodes().forEach { walk(it) }
        flush()
        return result
    }
}
