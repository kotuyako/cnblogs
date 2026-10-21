package dev.garden.reader.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class GardenRepository(context: Context, private val cookiesFor: (String) -> String? = { null }) {
    private val preferences = context.getSharedPreferences("garden", Context.MODE_PRIVATE)
    private val bodyDirectory = File(context.filesDir, "articles").apply { mkdirs() }
    private val nextPages = java.util.concurrent.ConcurrentHashMap<Channel, String>()
    fun hasMore(channel: Channel) = nextPages.containsKey(channel)

    var theme: String
        get() = preferences.getString("theme", "system") ?: "system"
        set(value) { preferences.edit().putString("theme", value).apply() }
    var fontSize: Float
        get() = preferences.getFloat("font", 18f)
        set(value) { preferences.edit().putFloat("font", value).apply() }
    var dynamicColor: Boolean
        get() = preferences.getBoolean("dynamic_color", true)
        set(value) { preferences.edit().putBoolean("dynamic_color", value).apply() }

    fun cached(channel: Channel): List<Article> = decode(preferences.getString("website_${channel.name}", "[]")!!)
    fun bookmarks(): List<Article> = decode(preferences.getString("bookmarks", "[]")!!)
    fun saveBookmarks(articles: List<Article>) {
        preferences.edit().putString("bookmarks", encode(articles)).apply()
    }

    suspend fun refresh(channel: Channel, onInitial: (List<Article>) -> Unit = {}): List<Article> = withContext(Dispatchers.IO) {
        val page = WebContentParser.parse(download(channel.pageUrl).content, channel.pageUrl)
        if (page.next != null) nextPages[channel] = page.next else nextPages.remove(channel)
        val parsed = page.articles
        onInitial(parsed)
        val slots = Semaphore(3)
        val result = supervisorScope {
            parsed.mapIndexed { index, article -> async {
                if (article.image != null || index >= 8) article else slots.withPermit {
                    try {
                        val document = org.jsoup.Jsoup.parse(download(article.url).content, article.url)
                        val image = document.selectFirst("#cnblogs_post_body img, #news_body img, #ArticleCnt img")
                        val raw = image?.let { it.attr("data-original").ifBlank { it.attr("data-src") }.ifBlank { it.attr("src") } }
                            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
                        article.copy(image = raw?.let { ContentParser.safeWebUrl(URI(article.url).resolve(it).toString()) }
                            ?.replaceFirst("http://", "https://"))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { article }
                }
            } }.awaitAll()
        }
        check(result.isNotEmpty()) { "暂时无法解析该分类的网页文章，请稍后重试" }
        preferences.edit().putString("website_${channel.name}", encode(result)).apply()
        result
    }

    suspend fun more(channel: Channel): List<Article> = withContext(Dispatchers.IO) {
        val target = nextPages[channel] ?: return@withContext emptyList()
        val page = WebContentParser.parse(download(target).content, target)
        check(page.articles.isNotEmpty()) { "下一页暂时没有可读取的文章" }
        if (page.next != null) nextPages[channel] = page.next else nextPages.remove(channel)
        page.articles
    }

    suspend fun body(article: Article): String = withContext(Dispatchers.IO) {
        val key = MessageDigest.getInstance("SHA-256").digest(article.url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = AtomicFile(File(bodyDirectory, "$key.html"))
        runCatching { file.openRead().bufferedReader().use { it.readText() } }
            .getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val response = download(article.url)
        val result = ContentParser.extractBody(response.content, article.url)
        // Authenticated content remains in memory; logout must not leave private bodies on disk.
        if (response.usedSession) return@withContext result
        val stream = file.startWrite()
        try {
            stream.write(result.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        result
    }

    suspend fun clearBodyCache() = withContext(Dispatchers.IO) {
        bodyDirectory.listFiles()?.filter { it.isFile && (it.name.endsWith(".html") || it.name.endsWith(".bak")) }
            ?.forEach { it.delete() }
    }

    private data class DownloadResult(val content: String, val usedSession: Boolean)

    private fun download(url: String): DownloadResult {
        var target = url
        var usedSession = false
        repeat(6) {
            require(ContentParser.isCnblogsUrl(target) && target.startsWith("https://")) { "无效的博客园链接" }
            check(URI(target).host != "account.cnblogs.com") { "这篇内容需要登录博客园，请先登录再重试" }
            val connection = URI(target).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "GardenReader/1.3 (Android; unofficial native website reader)")
                // Recompute for every redirect instead of forwarding another host's cookies.
                cookiesFor(target)?.takeIf { it.isNotBlank() }?.let {
                    usedSession = true
                    connection.setRequestProperty("Cookie", it)
                }
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("重定向地址为空")
                    target = URI(target).resolve(location).toString()
                } else {
                    check(status == 200) { "服务器返回 HTTP $status，请稍后重试" }
                    val content = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 4 * 1024 * 1024) { "内容过大，请打开原文" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    return DownloadResult(content, usedSession)
                }
            } finally { connection.disconnect() }
        }
        error("服务器重定向次数过多")
    }

    private fun encode(articles: List<Article>): String = JSONArray().apply {
        articles.forEach { article ->
            put(JSONObject().put("url", article.url).put("title", article.title)
                .put("author", article.author).put("summary", article.summary).put("published", article.published)
                .put("image", article.image ?: "").put("avatar", article.avatar ?: "")
                .put("views", article.views).put("likes", article.likes).put("comments", article.comments)
                .put("blogApp", article.blogApp))
        }
    }.toString()

    private fun decode(json: String): List<Article> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Article(item.getString("url"), item.getString("title"), item.optString("author"),
                item.optString("summary"), item.optString("published"), item.optString("image").takeIf { it.isNotBlank() },
                item.optString("avatar").takeIf { it.isNotBlank() },
                if (item.isNull("views")) null else item.optInt("views"),
                if (item.isNull("likes")) null else item.optInt("likes"),
                if (item.isNull("comments")) null else item.optInt("comments"),
                item.optString("blogApp").takeIf { it.isNotBlank() && it != "null" })
        }
    }.getOrDefault(emptyList())
}
