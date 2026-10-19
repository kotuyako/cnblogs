package dev.garden.reader.features

import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.WebContentParser
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

data class NativeComment(val id: String, val author: String, val date: String, val body: String, val avatar: String?)

class Comments(private val account: GardenAccountSession) {
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
        account.apiRequest(target.path, body)
    }
}
