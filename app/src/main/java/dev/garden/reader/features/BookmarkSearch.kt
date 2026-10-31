package dev.garden.reader.features

import dev.garden.reader.data.Article
import java.net.URI
import java.text.Normalizer
import java.util.Locale

object BookmarkSearch {
    fun filter(items: List<Article>, query: String, kind: Int = 0, titleOnly: Boolean = false,
        cachedBodies: Map<String, String> = emptyMap()): List<Article> {
        require(kind in 0..2)
        val terms = normalize(query).split(' ').filter(String::isNotBlank)
        return items.filter { article ->
            val news = runCatching { URI(article.url).host.equals("news.cnblogs.com", true) }.getOrDefault(false)
            val searchable = normalize(if (titleOnly) article.title else
                "${article.title} ${article.author} ${article.summary} ${cachedBodies[article.url].orEmpty()}")
            (kind == 0 || kind == 1 && !news || kind == 2 && news) && terms.all(searchable::contains)
        }
    }

    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[\\s\\p{Z}]+"), " ").trim()
}
