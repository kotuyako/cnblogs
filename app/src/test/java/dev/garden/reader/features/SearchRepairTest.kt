package dev.garden.reader.features

import dev.garden.reader.data.Article
import org.junit.Assert.*
import org.junit.Test

class SearchRepairTest {
    private val blog = Article("https://www.cnblogs.com/alice/p/123", "Kotlin 开发", "Alice", "Compose 组件", "")
    private val news = Article("https://news.cnblogs.com/n/456/", "苹果智能", "techseek", "科技新闻", "")
    private val newsHtml = """<div class="news_block"><div class="news_entry"><h2><a href="/n/456/">苹果智能</a></h2></div>
        <div class="entry_summary">公开的 AI 新闻摘要</div><div class="entry_footer"><a>techseek</a>
        <span class="comment">评论(2)</span><span class="view">35 人浏览</span>发布于 2026-10-31 10:08</div></div>
        <div class="pager"><a href="/n/page/2/">Next &gt;</a></div>"""

    @Test fun savedSearchMatchesPreviouslyReadBody() {
        val cache = mapOf(news.url to "正文中的卫星通信测试")
        assertEquals(listOf(news), BookmarkSearch.filter(listOf(blog, news), "卫星 通信", cachedBodies = cache))
    }
    @Test fun savedSearchNeverReturnsUnbookmarkedCacheEntries() {
        assertTrue(BookmarkSearch.filter(listOf(blog), "卫星", cachedBodies = mapOf(news.url to "卫星")).isEmpty())
    }
    @Test fun titleFilterExcludesAuthorsSummaryAndBody() {
        assertTrue(BookmarkSearch.filter(listOf(blog), "alice", titleOnly = true).isEmpty())
        assertTrue(BookmarkSearch.filter(listOf(news), "卫星", titleOnly = true,
            cachedBodies = mapOf(news.url to "卫星")).isEmpty())
    }
    @Test fun normalizesWidthCaseAndWhitespace() {
        assertEquals(listOf(blog), BookmarkSearch.filter(listOf(blog, news), "ＫＯＴＬＩＮ　compose"))
        assertEquals(listOf(blog), BookmarkSearch.filter(listOf(blog), "ALICE\n组件"))
    }
    @Test fun localKindFiltersIncludeActualSavedNews() {
        assertEquals(listOf(news), BookmarkSearch.filter(listOf(blog, news), "", kind = 2))
        assertEquals(listOf(blog), BookmarkSearch.filter(listOf(blog, news), "", kind = 1))
        assertEquals(listOf(blog, news), BookmarkSearch.filter(listOf(blog, news), ""))
    }
    @Test fun matchesPublicNewsFieldsAndShowsLimitedScope() {
        val page = SiteSearchParser.recentNews(listOf("https://news.cnblogs.com/" to newsHtml), "ai", 1)
        assertEquals(1, page.articles.size)
        assertEquals(news.url, page.articles.single().url)
        assertEquals("techseek", page.articles.single().author)
        assertEquals(2, page.articles.single().comments)
        assertEquals(35, page.articles.single().views)
        assertTrue(page.hasMore); assertTrue(page.recentNews)
        assertTrue(page.notice.orEmpty().contains("非全站历史搜索"))
    }
    @Test fun zeroMatchesStillOffersNextPublicBatch() {
        val page = SiteSearchParser.recentNews(listOf("https://news.cnblogs.com/" to newsHtml), "不存在的关键词", 1)
        assertTrue(page.articles.isEmpty()); assertTrue(page.hasMore)
    }
    @Test fun captchaIsNotEmptySearchSuccess() {
        val failure = runCatching { SiteSearchParser.newsPage("<p>请完成人机验证</p><button id=captcha-button>验证</button>",
            "https://news.cnblogs.com/") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("人机验证"))
    }
    @Test fun brokenMarkupIsNotEmptySearchSuccess() {
        assertTrue(runCatching { SiteSearchParser.newsPage("<nav>首页</nav>", "https://news.cnblogs.com/") }.isFailure)
    }
    @Test fun ignoresForeignLinksInsidePublicNewsMarkup() {
        val html = newsHtml.replace("href=\"/n/456/\"", "href=\"https://evil.example/n/456/\"")
        assertTrue(runCatching { SiteSearchParser.newsPage(html, "https://news.cnblogs.com/") }.isFailure)
    }
    @Test fun documentedEmptyApiBodyIsValidEmptyList() {
        assertTrue(SiteSearchParser.api("  ", "News").isEmpty())
    }
}
