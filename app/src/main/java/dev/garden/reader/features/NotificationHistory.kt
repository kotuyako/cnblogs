package dev.garden.reader.features

import android.content.Context
import dev.garden.reader.data.Article
import dev.garden.reader.data.ContentParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class NotificationInboxState(
    val enabled: Boolean = false,
    val initialized: Boolean = false,
    val checking: Boolean = false,
    val lastCheckedAt: Long = 0,
    val lastError: String? = null,
    val lastFreshCount: Int = 0,
    val records: List<ArticleNotice> = emptyList(),
) {
    val unreadCount: Int get() = records.count { !it.read }
}

/** Only public article metadata is stored. No account credentials, cookies or response bodies. */
class NotificationHistory internal constructor(context: Context) {
    private val preferences = context.getSharedPreferences("article_notifications", Context.MODE_PRIVATE)
    private val lock = Any()
    private var revision = 0L
    private var seen: List<String>? = readSeen()
    private val mutable = MutableStateFlow(NotificationInboxState(
        enabled = preferences.getBoolean("enabled", false), initialized = seen != null,
        lastCheckedAt = preferences.getLong("last_checked", 0), lastError = preferences.getString("last_error", null),
        lastFreshCount = preferences.getInt("last_fresh", 0), records = readRecords(),
    ))
    val state: StateFlow<NotificationInboxState> = mutable.asStateFlow()

    internal fun enabled(value: Boolean) = synchronized(lock) {
        if (mutable.value.enabled == value) return@synchronized
        revision++
        if (value) seen = null
        mutable.value = mutable.value.copy(enabled = value, initialized = seen != null, checking = false, lastError = null)
        persist()
    }

    internal fun beginCheck(): Long? = synchronized(lock) {
        if (!mutable.value.enabled) return@synchronized null
        mutable.value = mutable.value.copy(checking = true, lastError = null)
        revision
    }

    internal fun accept(expectedRevision: Long, articles: List<Article>, now: Long): NotificationDelta? = synchronized(lock) {
        if (revision != expectedRevision || !mutable.value.enabled) return@synchronized null
        val delta = NotificationPolicy.compare(seen, articles)
        seen = delta.seen
        mutable.value = mutable.value.copy(initialized = true, checking = false, lastCheckedAt = now,
            lastError = null, lastFreshCount = delta.fresh.size,
            records = NotificationPolicy.merge(mutable.value.records, delta.fresh, now))
        persist()
        delta
    }

    internal fun failed(expectedRevision: Long, message: String) = synchronized(lock) {
        if (revision != expectedRevision) return@synchronized
        mutable.value = mutable.value.copy(checking = false, lastError = message)
        persist()
    }

    internal fun cancelled(expectedRevision: Long) = synchronized(lock) {
        if (revision == expectedRevision) mutable.value = mutable.value.copy(checking = false)
    }

    internal fun markNotified(urls: Set<String>) = synchronized(lock) {
        mutable.value = mutable.value.copy(records = mutable.value.records.map {
            if (it.article.url in urls) it.copy(notified = true) else it
        })
        persist()
    }

    fun markRead(url: String) = synchronized(lock) {
        mutable.value = mutable.value.copy(records = mutable.value.records.map {
            if (it.article.url == url) it.copy(read = true) else it
        })
        persist()
    }

    fun markAllRead() = synchronized(lock) {
        mutable.value = mutable.value.copy(records = mutable.value.records.map { it.copy(read = true) })
        persist()
    }

    private fun persist() {
        val value = mutable.value
        preferences.edit().putBoolean("enabled", value.enabled).putLong("last_checked", value.lastCheckedAt)
            .putString("last_error", value.lastError).putInt("last_fresh", value.lastFreshCount)
            .putString("seen_json", seen?.let { JSONArray(it).toString() }).remove("seen")
            .putString("history", JSONArray().apply { value.records.forEach { notice ->
                val article = notice.article
                put(JSONObject().put("url", article.url).put("title", article.title).put("author", article.author)
                    .put("summary", article.summary).put("published", article.published)
                    .put("image", article.image).put("avatar", article.avatar).put("blogApp", article.blogApp)
                    .put("views", article.views).put("likes", article.likes).put("comments", article.comments)
                    .put("discovered", notice.discoveredAt).put("read", notice.read).put("notified", notice.notified))
            } }.toString()).apply()
    }

    private fun readSeen(): List<String>? {
        val json = preferences.getString("seen_json", null)
        if (json != null) return runCatching { JSONArray(json).let { array ->
            (0 until array.length()).map { array.getString(it) }.take(1000)
        } }.getOrNull()
        // Upgrade from the original background checker without announcing the baseline again.
        return preferences.getStringSet("seen", null)?.toList()
    }

    private fun readRecords(): List<ArticleNotice> = runCatching {
        val array = JSONArray(preferences.getString("history", "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching record@ {
            val item = array.getJSONObject(index)
            val url = item.getString("url")
            if (!url.startsWith("https://") || !ContentParser.isCnblogsUrl(url)) return@record null
            fun optional(key: String) = item.optString(key).takeIf { it.isNotBlank() && it != "null" }
            fun number(key: String) = if (item.isNull(key)) null else item.optInt(key)
            val article = Article(url, item.getString("title"), item.optString("author"), item.optString("summary"),
                item.optString("published"), optional("image"), optional("avatar"), number("views"), number("likes"),
                number("comments"), optional("blogApp"))
            ArticleNotice(article, item.optLong("discovered"), item.optBoolean("read"), item.optBoolean("notified"))
        }.getOrNull() }.distinctBy { it.article.url }.take(200)
    }.getOrDefault(emptyList())

    companion object {
        @Volatile private var instance: NotificationHistory? = null
        fun get(context: Context): NotificationHistory = instance ?: synchronized(this) {
            instance ?: NotificationHistory(context.applicationContext).also { instance = it }
        }
    }
}
