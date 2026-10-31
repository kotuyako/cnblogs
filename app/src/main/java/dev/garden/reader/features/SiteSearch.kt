package dev.garden.reader.features

import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.account.ApiRequestFailure
import dev.garden.reader.data.Article
import dev.garden.reader.data.ContentParser
import dev.garden.reader.data.WebContentParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.io.IOException

data class SiteSearchPage(val articles: List<Article>, val hasMore: Boolean, val notice: String? = null,
    val recentNews: Boolean = false)

class SiteSearch(private val account: GardenAccountSession) {
    suspend fun search(query: String, news: Boolean, page: Int, category: String = if (news) "News" else "Blog"): List<Article> {
        return searchPage(query, news, page, category).articles
    }

    suspend fun searchPage(query: String, news: Boolean, page: Int,
        category: String = if (news) "News" else "Blog", recentNews: Boolean = false): SiteSearchPage {
        require(category in setOf("Blog", "News", "Kb"))
        require(query.trim().length in 1..100) { "搜索词应为 1～100 字" }
        require(page in 1..10_000) { "无效搜索页码" }
        // The public global search currently requires an interactive CAPTCHA. Do not bypass it,
        // and do not represent its HTTP-200 challenge page as an empty result list.
        if (category == "News" && !recentNews && !account.state.value.loggedIn && page > 1)
            throw IOException("全站搜索授权已失效，请重新搜索以查询近期公开新闻。")
        if (category == "News" && (recentNews || !account.state.value.loggedIn))
            return this.recentNews(query, page)
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val raw = try { account.apiRequest("/ZzkDocuments/$category?keyWords=$encoded&pageIndex=$page") }
        catch (error: ApiRequestFailure) {
            if (category != "News" || error.status !in setOf(401, 403)) throw error
            // Start a distinct public-list cursor, never mix API page 2 with public-list batch 2.
            if (page != 1) throw IOException("全站搜索授权已失效，请重新搜索以查询近期公开新闻。")
            return this.recentNews(query, 1)
        }
        val result = SiteSearchParser.api(raw, category)
        return SiteSearchPage(result, result.isNotEmpty())
    }

    private suspend fun recentNews(query: String, page: Int): SiteSearchPage = withContext(Dispatchers.IO) {
        val first = (page - 1) * NEWS_BATCH_SIZE + 1
        val pages = mutableListOf<Pair<String, String>>()
        for (number in first until first + NEWS_BATCH_SIZE) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val url = if (number == 1) "https://news.cnblogs.com/" else "https://news.cnblogs.com/n/page/$number/"
            val html = downloadNews(url)
            pages += url to html
            if (SiteSearchParser.newsPage(html, url).next == null) break
        }
        SiteSearchParser.recentNews(pages, query, first)
    }

    private fun downloadNews(url: String): String {
        var target = URI(url)
        repeat(4) {
            require(target.scheme == "https" && target.host == "news.cnblogs.com" && target.userInfo == null && target.port == -1)
            val connection = target.toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 12_000; connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "GardenReader/1.6 (Android; unofficial native website reader)")
                connection.setRequestProperty("Accept", "text/html")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    target = target.resolve(connection.getHeaderField("Location") ?: throw IOException("新闻列表重定向为空"))
                } else {
                    if (status != 200) throw IOException("公开新闻列表读取失败（HTTP $status），请重试。")
                    return connection.inputStream.use { input ->
                        val bytes = ByteArray(8192); val output = java.io.ByteArrayOutputStream()
                        while (true) {
                            val count = input.read(bytes); if (count < 0) break
                            check(output.size() + count <= 4 * 1024 * 1024) { "新闻列表响应过大" }
                            output.write(bytes, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                }
            } finally { connection.disconnect() }
        }
        throw IOException("新闻列表重定向次数过多")
    }

    companion object { const val NEWS_BATCH_SIZE = 3 }
}

/** Pure parsers: an error/challenge is never a legitimate zero-result response. */
object SiteSearchParser {
    fun api(raw: String, category: String): List<Article> {
        require(category in setOf("Blog", "News", "Kb"))
        // The official API documents an empty HTTP-200 body for no results.
        if (raw.isBlank()) return emptyList()
        val value = JSONTokener(raw).nextValue()
        val rows = when (value) {
            is JSONArray -> value
            is JSONObject -> listOf("Documents", "documents", "Data", "data", "Items", "items")
                .firstNotNullOfOrNull { value.optJSONArray(it) } ?: error("搜索接口返回了未知格式，未将其当作空结果。")
            else -> error("搜索接口没有返回文章列表")
        }
        val result = (0 until rows.length()).mapNotNull { index ->
            val item = rows.getJSONObject(index)
            fun field(vararg keys: String) = keys.firstNotNullOfOrNull { item.optString(it).takeIf { v -> v.isNotBlank() && v != "null" } }.orEmpty()
            val url = field("Uri", "uri", "Url", "url", "LinkUrl").replaceFirst("http://", "https://")
            if (!ContentParser.isCnblogsUrl(url)) return@mapNotNull null
            val host = URI(url).host.lowercase()
            if (category == "News" && host != "news.cnblogs.com" || category == "Blog" && host !in listOf("www.cnblogs.com", "cnblogs.com") || category == "Kb" && host != "kb.cnblogs.com") return@mapNotNull null
            val title = Jsoup.parse(field("Title", "title")).text()
            if (title.isBlank()) return@mapNotNull null
            fun count(vararg keys: String) = field(*keys).toIntOrNull()?.takeIf { it >= 0 }
            val alias = field("BlogApp", "blogApp", "UserAlias", "userAlias")
            Article(url, title, field("Author", "author", "UserName", "userName"),
                Jsoup.parse(field("Content", "content", "Summary", "summary", "Description", "description")).text().take(1000),
                field("PublishTime", "publishTime", "PostDate", "postDate", "DateAdded", "dateAdded").replace('T', ' ').take(19),
                views = count("ViewTimes", "viewTimes", "ViewCount", "viewCount"),
                likes = count("VoteTimes", "voteTimes", "DiggCount", "diggCount"),
                comments = count("CommentTimes", "commentTimes", "CommentCount", "commentCount"),
                blogApp = AuthorRef.validBlog(alias) ?: AuthorRef.fromUrl(alias, "", null)?.blog
                    ?: AuthorRef.fromUrl(url, "", null)?.blog)
        }.distinctBy { it.url }
        check(rows.length() == 0 || result.isNotEmpty()) { "搜索结果格式暂不支持，未将其当作没有结果。" }
        return result
    }

    fun newsPage(html: String, url: String): dev.garden.reader.data.WebsitePage {
        require(URI(url).host == "news.cnblogs.com")
        val doc = Jsoup.parse(html, url)
        check(doc.select("#captcha-button, script[src*=captcha]").isEmpty() && !doc.text().contains("请完成人机验证")) {
            "新闻网站要求人机验证，未将验证页面当作空结果。"
        }
        val parsed = WebContentParser.parse(html, url)
        val valid = parsed.articles.filter { runCatching { URI(it.url).host == "news.cnblogs.com" }.getOrDefault(false) }
        check(valid.isNotEmpty()) { "新闻列表结构已变化，未将无法解析当作没有结果。" }
        val current = Regex("/n/page/(\\d+)/?").matchEntire(URI(url).path)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        // Current news markup uses "Next >", which is not accepted by the older shared parser.
        val next = doc.select(".pager a[href]").mapNotNull { anchor ->
            runCatching { URI(url).resolve(anchor.attr("href")) }.getOrNull()
        }.firstOrNull {
            it.scheme == "https" && it.host == "news.cnblogs.com" && it.userInfo == null && it.port == -1 &&
                it.path.trimEnd('/') == "/n/page/${current + 1}"
        }?.toString()
        return parsed.copy(articles = valid, next = next)
    }

    fun recentNews(pages: List<Pair<String, String>>, query: String, firstPage: Int): SiteSearchPage {
        require(pages.isNotEmpty() && firstPage > 0)
        val parsed = pages.map { (url, html) -> newsPage(html, url) }
        val articles = BookmarkSearch.filter(parsed.flatMap { it.articles }.distinctBy { it.url }, query, 2)
        val end = firstPage + pages.size - 1
        return SiteSearchPage(articles, parsed.last().next != null,
            "近期新闻匹配 · 本批检索公开新闻列表第 $firstPage～$end 页的标题、摘要和作者；非全站历史搜索。全站搜索需要有效 API 授权，网页搜索当前要求人机验证。",
            recentNews = true)
    }
}
