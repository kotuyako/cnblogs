package dev.garden.reader.features

import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.Article
import dev.garden.reader.data.ContentParser
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

class SiteSearch(private val account: GardenAccountSession) {
    suspend fun search(query: String, news: Boolean, page: Int, category: String = if (news) "News" else "Blog"): List<Article> {
        require(category in setOf("Blog", "News", "Kb"))
        require(query.trim().length in 1..100) { "搜索词应为 1～100 字" }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val value = JSONTokener(account.apiRequest("/ZzkDocuments/$category?keyWords=$encoded&pageIndex=$page")).nextValue()
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
            val host = URI(url).host
            if (category == "News" && host != "news.cnblogs.com" || category == "Blog" && host !in listOf("www.cnblogs.com", "cnblogs.com") || category == "Kb" && host != "kb.cnblogs.com") return@mapNotNull null
            fun count(vararg keys: String) = field(*keys).toIntOrNull()
            Article(url, Jsoup.parse(field("Title", "title")).text(), field("Author", "author", "UserName", "userName"),
                Jsoup.parse(field("Content", "content", "Summary", "summary", "Description", "description")).text().take(1000),
                field("PublishTime", "publishTime", "PostDate", "postDate", "DateAdded", "dateAdded").replace('T', ' ').take(19),
                views = count("ViewTimes", "viewTimes", "ViewCount", "viewCount"),
                likes = count("VoteTimes", "voteTimes", "DiggCount", "diggCount"),
                comments = count("CommentTimes", "commentTimes", "CommentCount", "commentCount"),
                blogApp = AuthorRef.validBlog(field("BlogApp", "blogApp")))
        }.distinctBy { it.url }
        check(rows.length() == 0 || result.isNotEmpty()) { "搜索结果格式暂不支持，未将其当作没有结果。" }
        return result
    }
}
