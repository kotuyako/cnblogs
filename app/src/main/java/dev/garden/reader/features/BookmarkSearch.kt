package dev.garden.reader.features

import dev.garden.reader.data.Article
import java.net.URI

object BookmarkSearch {
    fun filter(items: List<Article>, query: String, kind: Int = 0, titleOnly: Boolean = false): List<Article> {
        val term = query.trim()
        return items.filter { article ->
            val news = runCatching { URI(article.url).host == "news.cnblogs.com" }.getOrDefault(false)
            (kind == 0 || kind == 1 && !news || kind == 2 && news) && (term.isBlank() ||
                (if (titleOnly) article.title else "${article.title} ${article.author} ${article.summary}").contains(term, true))
        }
    }
}
