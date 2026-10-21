package dev.garden.reader.features

import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.Article
import dev.garden.reader.data.WebContentParser
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuthorProfile(val title: String?, val subtitle: String?, val postCount: Int?, val pageSize: Int?)

/** Keys follow https://api.cnblogs.com/help; unknown envelopes are errors, not empty lists. */
object AuthorApiParser {
    fun profile(raw: String): AuthorProfile {
        val value = JSONObject(raw)
        check(listOf("blogId", "title", "subtitle", "postCount", "pageSize").any(value::has)) { "作者资料接口返回了未知 JSON 结构" }
        fun text(key: String) = value.opt(key)?.takeIf { it is String }?.toString()?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text() }
        fun number(key: String) = (value.opt(key) as? Number)?.toInt()?.takeIf { it >= 0 }
        return AuthorProfile(text("title"), text("subtitle"), number("postCount"), number("pageSize")?.takeIf { it > 0 })
    }
    fun posts(raw: String, author: AuthorRef): List<Article> {
        val rows = JSONArray(raw)
        val result = (0 until rows.length()).mapNotNull { index ->
            val item = rows.getJSONObject(index)
            fun text(key: String) = (item.opt(key) as? String).orEmpty()
            val blog = if (text("BlogApp").isBlank()) author.blog else
                requireNotNull(AuthorRef.validBlog(text("BlogApp"))) { "接口返回了无效 BlogApp" }
            check(blog.equals(author.blog, true)) { "作者文章接口返回了其他博客的数据" }
            val url = WebContentParser.imageUrl(text("Url"), author.url) ?: return@mapNotNull null
            val linked = AuthorRef.from(Article(url, "", "", "", "")) ?: return@mapNotNull null
            if (!linked.blog.equals(author.blog, true)) return@mapNotNull null
            val title = Jsoup.parse(text("Title")).text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            fun number(key: String) = (item.opt(key) as? Number)?.toInt()?.takeIf { it >= 0 }
            Article(url, title, text("Author").ifBlank { author.name }, Jsoup.parse(text("Description")).text(),
                text("PostDate").replace('T', ' ').take(19),
                avatar = WebContentParser.imageUrl(text("Avatar"), author.url) ?: author.avatar,
                views = number("ViewCount"), comments = number("CommentCount"), likes = number("DiggCount"), blogApp = blog)
        }.distinctBy { it.url }
        check(rows.length() == 0 || result.isNotEmpty()) { "作者文章接口未返回可识别的博文，不能当作空列表" }
        return result
    }
}

interface AuthorDataSource {
    suspend fun profile(author: AuthorRef): AuthorProfile
    suspend fun posts(author: AuthorRef, page: Int): List<Article>
}

/** Reuses the app's encrypted PAT session, host restriction, timeouts and bounded responses. */
class AuthorRepository(private val account: GardenAccountSession) : AuthorDataSource {
    private fun path(author: AuthorRef): String {
        require(AuthorRef.validBlog(author.blog) != null) { "无法可靠识别博客标识" }
        return "/blogs/${author.blog}"
    }
    override suspend fun profile(author: AuthorRef): AuthorProfile {
        val raw = account.apiRequest(path(author))
        return withContext(Dispatchers.Default) { AuthorApiParser.profile(raw) }
    }
    override suspend fun posts(author: AuthorRef, page: Int): List<Article> {
        require(page > 0)
        val raw = account.apiRequest("${path(author)}/posts?pageIndex=$page")
        return withContext(Dispatchers.Default) { AuthorApiParser.posts(raw, author) }
    }
}
