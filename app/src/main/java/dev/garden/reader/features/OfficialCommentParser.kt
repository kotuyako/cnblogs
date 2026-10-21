package dev.garden.reader.features

import dev.garden.reader.data.WebContentParser
import org.json.JSONArray
import org.jsoup.Jsoup

/** Documented PascalCase schema only. An auth/error object must never become an empty page. */
object OfficialCommentParser {
    fun parse(raw: String): List<NativeComment> {
        val rows = JSONArray(raw)
        return (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            val id = item.opt("Id")?.toString()?.takeIf { it.matches(Regex("[0-9]+")) }
                ?: error("评论接口返回了无效 Id")
            fun text(key: String) = (item.opt(key) as? String).orEmpty()
            check(item.opt("Body") is String && item.opt("Author") is String && item.opt("DateAdded") is String) {
                "评论接口返回了未知 JSON 字段，未将其当作空评论"
            }
            NativeComment(id, text("Author"), text("DateAdded").replace('T', ' ').take(19),
                Jsoup.parse(text("Body")).apply { select("script, style, iframe").remove() }.text(),
                WebContentParser.imageUrl(text("FaceUrl"), "https://api.cnblogs.com/"),
                text("AuthorUrl").takeIf { it.isNotBlank() })
        }.distinctBy { it.id }
    }
}
