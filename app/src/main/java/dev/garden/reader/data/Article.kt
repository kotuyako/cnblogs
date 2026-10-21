package dev.garden.reader.data

data class Article(
    val url: String,
    val title: String,
    val author: String,
    val summary: String,
    val published: String,
    val image: String? = null,
    val avatar: String? = null,
    val views: Int? = null,
    val likes: Int? = null,
    val comments: Int? = null,
    val blogApp: String? = null,
)

enum class Channel(val label: String, val pageUrl: String) {
    HOME("最新", "https://www.cnblogs.com/"),
    VIEWS("48小时阅读排行", "https://www.cnblogs.com/aggsite/topviews"),
    DIGGS("10天推荐排行", "https://www.cnblogs.com/aggsite/topdiggs"),
    PICKED("精华", "https://www.cnblogs.com/pick/"),
    KNOWLEDGE("知识库", "https://kb.cnblogs.com/"),
    NEWS("新闻", "https://news.cnblogs.com/"),
}
