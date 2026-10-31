package dev.garden.reader.features

import dev.garden.reader.data.Article

/** Pure diff policy: first successful check is a baseline, not a batch of old notifications. */
internal data class NotificationDelta(val seen: List<String>, val fresh: List<Article>, val baseline: Boolean)

internal object NotificationPolicy {
    fun compare(previous: List<String>?, articles: List<Article>): NotificationDelta {
        val current = articles.distinctBy { it.url }
        val seen = (current.map { it.url } + previous.orEmpty()).distinct().take(1000)
        return NotificationDelta(seen, if (previous == null) emptyList() else current.filter { it.url !in previous }, previous == null)
    }

    fun merge(existing: List<ArticleNotice>, fresh: List<Article>, now: Long): List<ArticleNotice> =
        (fresh.filter { article -> existing.none { it.article.url == article.url } }
            .map { ArticleNotice(it, now) } + existing).take(200)
}

data class ArticleNotice(
    val article: Article,
    val discoveredAt: Long,
    val read: Boolean = false,
    val notified: Boolean = false,
)
