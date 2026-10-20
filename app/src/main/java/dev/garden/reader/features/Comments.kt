package dev.garden.reader.features

import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.WebContentParser
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import dev.garden.reader.data.Article

class Comments(private val account: GardenAccountSession) {
    suspend fun read(article: Article, target: CommentTarget, page: Int): CommentPage {
        var publicFailure: Exception? = null
        try {
            return withContext(Dispatchers.IO) {
                val id = target.path.split('/').let { if (target.news) it[2] else it[4] }
                val host = if (target.news) "news.cnblogs.com" else "www.cnblogs.com"
                val url = if (target.news) "https://$host/CommentAjax/GetComments?contentId=$id" else {
                    val blog = target.path.split('/')[2]
                    "https://$host/$blog/ajax/comments-block?postId=$id&loadCommentBox=false" + if (page > 1) "&pageIndex=$page" else ""
                }
                val (status, raw) = PublicHttp.get(url, host)
                check(status == 200) { "公开评论接口 HTTP $status" }
                val json = if (!target.news) JSONObject(raw) else null
                val html = json?.optString("comments") ?: raw
                val count = json?.optInt("commentCount", -1) ?: (article.comments ?: -1)
                val items = PublicCommentParser.parse(html, article.url)
                check(items.isNotEmpty() || count == 0) { "公开接口未返回评论正文，不能判断为暂无评论" }
                CommentPage(items, !target.news && items.isNotEmpty() && (count < 0 || page * items.size < count), "博客园网页公开评论")
            }
        } catch (c: CancellationException) { throw c } catch (e: Exception) { publicFailure = e }
        if (!account.state.value.loggedIn) error("${publicFailure?.message}。可授权后尝试官方评论 API；不会将接口受限显示成暂无评论。")
        val items = list(target, page)
        return CommentPage(items, items.size == 20, "博客园官方评论 API")
    }
    suspend fun list(target: CommentTarget, page: Int): List<NativeComment> {
        val array = JSONArray(account.apiRequest("${target.path}?pageIndex=$page&pageSize=20"))
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            fun field(vararg keys: String) = keys.firstNotNullOfOrNull {
                item.optString(it).takeIf { v -> v.isNotBlank() && v != "null" }
            }.orEmpty()
            NativeComment(field("Id", "id", "commentID"), field("Author", "author", "userName"),
                field("DateAdded", "dateAdded").replace('T', ' ').take(19),
                Jsoup.parse(field("Body", "body", "commentContent")).text(),
                WebContentParser.imageUrl(field("FaceUrl", "faceUrl"), "https://api.cnblogs.com/"))
        }
    }
    suspend fun post(target: CommentTarget, text: String) {
        require(text.trim().length in 1..4000) { "评论长度应为 1～4000 字" }
        val body = if (target.news) JSONObject().put("ParentId", 0).put("Content", text.trim())
            else JSONObject().put("body", text.trim())
        val response = account.apiRequest(target.path, body)
        if (response.trimStart().startsWith("{")) {
            val result = JSONObject(response)
            for (key in listOf("success", "Success", "isSuccessStatusCode", "IsSuccessStatusCode")) {
                check(!result.has(key) || result.optBoolean(key)) { "服务器未确认评论发布成功，请先刷新查看。" }
            }
        }
    }
}
