package dev.garden.reader.data

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.net.URI

object ContentParser {
    fun safeWebUrl(value: String): String? = runCatching {
        val uri = URI(value.trim())
        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) null
        else uri.toASCIIString()
    }.getOrNull()

    fun isCnblogsUrl(value: String): Boolean = runCatching {
        val host = URI(value).host.orEmpty().lowercase()
        safeWebUrl(value) != null && (host == "cnblogs.com" || host.endsWith(".cnblogs.com"))
    }.getOrDefault(false)

    fun extractBody(html: String, url: String): String {
        val doc = Jsoup.parse(html, url)
        val body = doc.selectFirst("#cnblogs_post_body, #news_body")
            ?: (if (URI(url).host == "kb.cnblogs.com") doc.selectFirst("#ArticleCnt") else null)
            ?: error("网页暂未提供可提取正文，请重试或授权后通过官方 API 读取")
        return cleanBody(body.html(), url)
    }

    fun cleanBody(html: String, url: String): String {
        val body = Jsoup.parseBodyFragment(html, url).body()
        val allow = Safelist.relaxed().addTags("hr").addAttributes("code", "class").addAttributes("pre", "class")
            .addAttributes("h1", "id").addAttributes("h2", "id").addAttributes("h3", "id")
            .addAttributes("h4", "id").addAttributes("h5", "id").addAttributes("h6", "id")
            .addAttributes("a", "id", "name").addAttributes("p", "id").addAttributes("div", "id")
            .preserveRelativeLinks(true)
            .removeProtocols("img", "src", "http").addProtocols("img", "src", "https")
        body.select("img").forEach {
            val source = it.attr("data-original").ifBlank { it.attr("data-src") }.ifBlank { it.attr("src") }
            it.attr("src", runCatching { URI(url).resolve(source).toString() }.getOrDefault("")
                .replaceFirst("http://", "https://"))
        }
        // Preserve local fragments for native TOC links, but keep all other links absolute.
        body.select("a[href]").forEach { link ->
            val href = link.attr("href")
            if (!href.startsWith("#")) link.attr("href", runCatching { URI(url).resolve(href).toString() }.getOrDefault(""))
        }
        return Jsoup.clean(body.html(), url, allow)
    }
}
