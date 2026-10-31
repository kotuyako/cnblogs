package dev.garden.reader.features

data class AuthorProfilePresentation(val title: String, val subtitle: String, val count: String,
    val biography: String = "个人简介：该博客信息接口未提供用户个人简介", val stale: Boolean = false)

fun authorProfilePresentation(profile: AuthorProfile?, loading: Boolean, error: String?): AuthorProfilePresentation {
    if (profile == null && loading) return AuthorProfilePresentation("正在加载博客资料…", "博客副标题：加载中…", "文章总数：加载中…")
    if (profile == null && error != null) return AuthorProfilePresentation("博客资料读取失败", "博客副标题：本次未能获取", "文章总数：本次未能获取（不是 0 篇）")
    return AuthorProfilePresentation(profile?.title ?: "接口未提供博客标题",
        "博客副标题：${profile?.subtitle ?: "接口未提供"}",
        profile?.postCount?.let { "文章总数：$it 篇" } ?: "文章总数：接口未返回 postCount", stale = error != null)
}
