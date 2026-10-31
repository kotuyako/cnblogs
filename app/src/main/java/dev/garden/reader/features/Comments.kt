package dev.garden.reader.features

import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.account.ApiRequestFailure
import dev.garden.reader.data.WebContentParser
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import dev.garden.reader.data.Article

interface CommentDataSource {
    suspend fun read(article: Article, target: CommentTarget, page: Int): CommentPage
    suspend fun post(target: CommentTarget, text: String)
}

class Comments(private val account: GardenAccountSession) : CommentDataSource {
    override suspend fun read(article: Article, target: CommentTarget, page: Int): CommentPage {
        require(page > 0)
        // PAT and a website session are separate credentials. Never send PAT/cookies to public URLs.
        var officialFailure: Exception? = null
        if (account.state.value.loggedIn) {
            try {
                val rows = list(target, page)
                return CommentPage(rows, rows.size == if (target.news) 20 else 10, "博客园官方评论 API")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ApiRequestFailure) {
                if (error.status !in listOf(401, 403)) throw error
                officialFailure = error
            }
        }
        try {
            return withContext(Dispatchers.IO) {
                if (!target.news) {
                    val (status, raw) = PublicHttp.get(PublicCommentProtocol.blogUrl(target, page), "www.cnblogs.com")
                    check(status == 200) { "博客园网页评论接口 HTTP $status；未读取评论。" }
                    PublicCommentProtocol.blog(raw, article.url, page)
                } else {
                    val id = PublicCommentProtocol.newsId(target)
                    val (status, raw) = PublicHttp.get("https://news.cnblogs.com/CommentAjax/GetComments?contentId=$id", "news.cnblogs.com")
                    check(status == 200) { "博客园新闻网页评论接口 HTTP $status；未读取评论。" }
                    val permissionHtml = if (PublicCommentParser.parse(raw, article.url).isEmpty()) {
                        val (permissionStatus, html) = PublicHttp.get("https://news.cnblogs.com/NewsAjax/LoadDetailommentInfo?newsId=$id", "news.cnblogs.com")
                        html.takeIf { permissionStatus == 200 }
                    } else null
                    PublicCommentProtocol.news(raw, article.url, permissionHtml)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val apiStatus = (officialFailure as? ApiRequestFailure)?.status
            // Preserve the website's clear login gate instead of replacing it with a generic API error.
            if (apiStatus != null) throw PublicCommentFailure("${error.message ?: "网页评论读取失败"} 官方评论 API 也返回 HTTP $apiStatus，现有授权未被接受。")
            throw error
        }
    }
    suspend fun list(target: CommentTarget, page: Int): List<NativeComment> {
        val raw = account.apiRequest("${target.path}?pageIndex=$page&pageSize=${if (target.news) 20 else 10}")
        if (!target.news) return withContext(Dispatchers.Default) { OfficialCommentParser.parse(raw) }
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            fun field(vararg keys: String) = keys.firstNotNullOfOrNull {
                item.optString(it).takeIf { v -> v.isNotBlank() && v != "null" }
            }.orEmpty()
            NativeComment(field("Id", "id", "commentID"), field("Author", "author", "userName"),
                field("DateAdded", "dateAdded").replace('T', ' ').take(19),
                Jsoup.parse(field("Body", "body", "commentContent")).text(),
                WebContentParser.imageUrl(field("FaceUrl", "faceUrl"), "https://api.cnblogs.com/"),
                field("AuthorUrl", "authorUrl").takeIf { it.isNotBlank() })
        }
    }
    override suspend fun post(target: CommentTarget, text: String) {
        require(text.trim().length in 1..4000) { "评论长度应为 1～4000 字" }
        val body = if (target.news) JSONObject().put("ParentId", 0).put("Content", text.trim())
            else JSONObject().put("body", text.trim())
        val response = account.apiRequest(target.path, body)
        check(response.trim() != "false") { "服务器未确认发布成功，请先刷新查看。" }
        if (response.trimStart().startsWith("{")) {
            val result = JSONObject(response)
            for (key in listOf("success", "Success", "isSuccessStatusCode", "IsSuccessStatusCode")) {
                check(!result.has(key) || result.optBoolean(key)) { "服务器未确认评论发布成功，请先刷新查看。" }
            }
        }
    }
}
