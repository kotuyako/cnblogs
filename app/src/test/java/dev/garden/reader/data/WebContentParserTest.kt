package dev.garden.reader.data

import org.junit.Assert.*
import org.junit.Test

class WebContentParserTest {
    private val base = "https://www.cnblogs.com/"
    private val modern = """<article class='post-item'><a class='post-item-title' href='/author/p/123'>原生阅读</a>
        <p class='post-item-summary'><img class='avatar' src='//pic.cnblogs.com/avatar.png'>文章摘要</p>
        <footer class='post-item-foot'><a class='post-item-author'>园友</a><span>2026-10-18 18:00</span>
        <a title='阅读 256'>256</a><a title='推荐 12'>12</a><a title='评论 3'>3</a></footer>
        <figure><img src='https://img.cnblogs.com/cover.png'></figure></article>"""
    @Test fun parsesWebsiteMetadataAndSeparatesAvatarFromCover() {
        val article = WebContentParser.parse(modern, base).articles.single()
        assertEquals("https://www.cnblogs.com/author/p/123", article.url)
        assertEquals("园友", article.author)
        assertEquals("文章摘要", article.summary)
        assertEquals("https://pic.cnblogs.com/avatar.png", article.avatar)
        assertEquals("https://img.cnblogs.com/cover.png", article.image)
        assertEquals(256, article.views)
        assertEquals(12, article.likes)
        assertEquals(3, article.comments)
        assertEquals("2026-10-18 18:00", article.published)
    }
    @Test fun parsesNewsWebsiteWithoutUsingTopicLogoAsArticleImage() {
        val article = WebContentParser.parse("""<div class='news_block'><h2 class='news_entry'><a href='/n/123/'>新闻</a></h2>
            <div class='entry_summary'><img class='topic_img' src='/logo.png'>摘要</div>
            <div class='entry_footer'><a>编辑</a><span class='view'>88 人浏览</span><span class='comment'>评论(2)</span>
            <span>2026-10-18 12:00</span></div><span class='diggnum'>5</span></div>""", "https://news.cnblogs.com/").articles.single()
        assertEquals("https://news.cnblogs.com/n/123/", article.url)
        assertEquals(88, article.views)
        assertEquals(2, article.comments)
        assertNull(article.image)
    }
    @Test fun parsesKnowledgeWebsite() {
        val article = WebContentParser.parse("""<div class='aiticle_item'><div class='msg_title'><a href='/page/42/'>知识库</a></div>
            <div class='msg_summary'>原生正文</div><div class='msg_tag'><span class='view'>阅读(4516)</span>
            <span class='recommend'>推荐(9)</span>2025-12-06 14:21</div></div>""", "https://kb.cnblogs.com/").articles.single()
        assertEquals("https://kb.cnblogs.com/page/42/", article.url)
        assertEquals("博客园知识库", article.author)
        assertEquals(4516, article.views)
    }
    @Test fun rejectsLookalikeLinksAndRemovesDuplicates() {
        val bad = modern.replace("/author/p/123", "https://cnblogs.com.evil.test/p/123")
        assertEquals(1, WebContentParser.parse(modern + modern + bad, base).articles.size)
    }
    @Test fun paginationStaysOnOriginalHost() {
        assertEquals("https://www.cnblogs.com/sitehome/p/2", WebContentParser.parse(modern +
            "<div class='pager'><a href='/sitehome/p/2'>&gt;</a></div>", base).next)
        assertNull(WebContentParser.parse(modern + "<div class='pager'><a href='https://evil.test/2'>&gt;</a></div>", base).next)
    }
    @Test fun navigationAndAdvertisementsDoNotBecomeArticles() {
        assertTrue(WebContentParser.parse("<nav><a href='/a'>导航</a></nav><div class='ad'><a href='/ad'>广告</a></div>", base).articles.isEmpty())
    }
    @Test fun includesAllRequestedWebsiteCategories() {
        assertEquals(listOf("最新", "48小时阅读排行", "10天推荐排行", "精华", "知识库", "新闻"), Channel.entries.map { it.label })
        assertTrue(Channel.entries.all { it.pageUrl.startsWith("https://") && !it.pageUrl.contains("feed.") })
    }
}
