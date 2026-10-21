package dev.garden.reader

import android.os.Looper
import androidx.lifecycle.viewModelScope
import dev.garden.reader.data.Article
import dev.garden.reader.features.*
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@ConscryptMode(ConscryptMode.Mode.OFF)
class CommentsApiTest {
    private val article = Article("https://www.cnblogs.com/example/p/12.html", "标题", "昵称", "", "", blogApp = "actual")
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private class Fake : CommentDataSource {
        val pages = mutableListOf<Int>(); var writes = 0; var failRead = false; var failPost = false
        override suspend fun read(article: Article, target: CommentTarget, page: Int): CommentPage {
            pages += page; check(!failRead) { "测试读取失败" }
            val id = if (page > 1) "2" else "1"
            return CommentPage(listOf(NativeComment(id, "评论者", "2026-10-21", "正文", null)), true, "测试数据")
        }
        override suspend fun post(target: CommentTarget, text: String) { writes++; check(!failPost) { "测试超时" } }
    }
    @Test fun articleBlogAppWinsWithoutUsingDisplayName() {
        assertEquals("/blogs/actual/posts/12/comments", CommentTarget.from(article)?.path)
    }
    @Test fun documentedSchemaParsesOtherAuthorsAndTime() {
        val row = OfficialCommentParser.parse("""[{"Id":1,"Body":"<p>真实字段测试</p>","Author":"评论者","AuthorUrl":"https://www.cnblogs.com/example/","FaceUrl":"","Floor":1,"DateAdded":"2026-10-21T12:00:00+08:00"}]""").single()
        assertEquals("评论者", row.author); assertEquals("真实字段测试", row.body)
        assertEquals("2026-10-21 12:00:00", row.date); assertEquals("example", row.authorRef()?.blog)
        assertTrue(OfficialCommentParser.parse("[]").isEmpty())
    }
    @Test fun unauthorizedOrUnknownJsonNeverMeansEmpty() {
        assertThrows(Exception::class.java) { OfficialCommentParser.parse("""{"title":"Unauthorized","status":401}""") }
        assertThrows(Exception::class.java) { OfficialCommentParser.parse("""[{"id":1}]""") }
    }
    @Test fun failedPageRetriesSamePageAndStopsDuplicates() {
        val fake = Fake(); val model = CommentsViewModel(article, fake)
        try {
            model.start(true); idle(); fake.failRead = true; model.more(); idle()
            assertEquals(1, model.state.value.page); assertEquals(2, model.state.value.failedPage)
            fake.failRead = false; model.retry(); idle(); assertEquals(listOf(1,2,2), fake.pages)
            model.more(); idle(); assertEquals(2, model.state.value.items.size); assertFalse(model.state.value.hasMore)
        } finally { model.viewModelScope.cancel() }
    }
    @Test fun successRefreshesAndCannotRepostClearedDraft() {
        val fake = Fake(); val model = CommentsViewModel(article, fake)
        try {
            model.draft(" 内容 "); model.post(true); idle(); model.post(true); idle()
            assertEquals(1, fake.writes); assertEquals(listOf(1), fake.pages)
            assertEquals("", model.state.value.draft); assertEquals(1, model.state.value.page)
        } finally { model.viewModelScope.cancel() }
    }
    @Test fun uncertainPostRetainsDraftAndBlocksRepeatUntilRefresh() {
        val fake = Fake().apply { failPost = true }; val model = CommentsViewModel(article, fake)
        try {
            model.draft("待确认"); model.post(true); idle(); model.post(true); idle()
            assertEquals(1, fake.writes); assertEquals("待确认", model.state.value.draft); assertTrue(model.state.value.uncertain)
            model.refresh(); idle(); assertFalse(model.state.value.uncertain)
        } finally { model.viewModelScope.cancel() }
    }
    @Test fun loggedOutNeverPosts() {
        val fake = Fake(); val model = CommentsViewModel(article, fake)
        try { model.draft("内容"); model.post(false); idle(); assertEquals(0, fake.writes) }
        finally { model.viewModelScope.cancel() }
    }
}
