package dev.garden.reader

import dev.garden.reader.features.SiteSearchParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SearchApiRepairTest {
    @Test fun parsesDocumentedSearchFieldsAndUserAlias() {
        val row = """[{"Uri":"http://www.cnblogs.com/alice/p/123.html","Title":"<em>Kotlin</em> 文章",
            "UserName":"昵称不是标识","UserAlias":"alice","Content":"<b>摘要</b>",
            "PublishTime":"2026-10-31T12:34:56","VoteTimes":2,"ViewTimes":9,"CommentTimes":3}]"""
        val result = SiteSearchParser.api(row, "Blog").single()
        assertEquals("alice", result.blogApp); assertEquals("昵称不是标识", result.author)
        assertEquals("Kotlin 文章", result.title); assertEquals("摘要", result.summary)
        assertEquals(3, result.comments); assertEquals(9, result.views)
    }
    @Test fun mapsPublicNewsAndNeverCrossesCategory() {
        val raw = """[{"Uri":"https://news.cnblogs.com/n/123/","Title":"新闻","ViewTimes":null}]"""
        assertNull(SiteSearchParser.api(raw, "News").single().views)
        assertTrue(runCatching { SiteSearchParser.api(raw, "Blog") }.isFailure)
    }
    @Test fun errorObjectCannotBecomeAnEmptyList() {
        assertTrue(runCatching { SiteSearchParser.api("""{"title":"Unauthorized","status":401}""", "News") }.isFailure)
    }
}
