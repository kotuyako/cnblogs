package dev.garden.reader

import android.os.Looper
import androidx.lifecycle.viewModelScope
import dev.garden.reader.account.ApiRequestFailure
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
class AuthorProfileRepairTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    @Test fun subtitleIsNotBiographyAndCountIsNotListSize() {
        val profile = AuthorApiParser.profile("""{"blogId":40823,"title":"博客标题","subtitle":"博客副标题","postCount":33,"pageSize":10}""")
        val ui = authorProfilePresentation(profile, false, null)
        assertEquals(40823L, profile.blogId)
        assertEquals("文章总数：33 篇", ui.count)
        assertEquals("博客副标题：博客副标题", ui.subtitle)
        assertTrue(ui.biography.contains("未提供用户个人简介"))
        assertFalse(ui.biography.contains("博客副标题"))
    }
    @Test fun zeroMissingLoadingAndFailureAreDifferent() {
        assertEquals("文章总数：0 篇", authorProfilePresentation(AuthorProfile(null,null,0,null),false,null).count)
        assertTrue(authorProfilePresentation(AuthorProfile(null,null,null,null),false,null).count.contains("未返回 postCount"))
        assertTrue(authorProfilePresentation(null,true,null).count.contains("加载中"))
        assertTrue(authorProfilePresentation(null,false,"HTTP 401").count.contains("未能获取"))
        assertTrue(authorProfilePresentation(AuthorProfile(null,null,33,null),false,"HTTP 401").stale)
    }
    @Test fun httpProblemTitleCannotBecomeBlogTitle() {
        assertThrows(Exception::class.java) { AuthorApiParser.profile("""{"type":"about:blank","title":"Unauthorized","status":401}""") }
        assertThrows(Exception::class.java) { AuthorApiParser.profile("""{"title":"Unauthorized"}""") }
    }
    @Test fun neverTruncatesOrInventsCount() {
        for (value in listOf("-1", "1.5", "2147483648", "\"33\"")) {
            assertThrows(Exception::class.java) { AuthorApiParser.profile("{\"postCount\":$value}") }
        }
        assertNull(AuthorApiParser.profile("""{"postCount":null}""").postCount)
    }
    @Test fun twoAuthorParameterChainsAndCounts() {
        for ((blog,count) in listOf("Tomorrowland" to 7,"cmt" to 13)) {
            val ref = AuthorRef.from(Article("https://www.cnblogs.com/wrong/p/1.html", "", "昵称不是博客标识", "", "", blogApp=blog))!!
            assertEquals(blog,ref.blog)
            val posts = AuthorApiParser.posts("""[{"Title":"标题","Url":"https://www.cnblogs.com/$blog/p/1.html","BlogApp":"$blog"}]""",ref)
            assertEquals(blog,AuthorRef.from(posts.single())?.blog)
            val profile = AuthorApiParser.profile("{\"postCount\":$count}")
            assertEquals(count, profile.postCount)
            assertEquals(1,posts.size)
        }
    }
    @Test fun errorResponseOnlyExposesSafeIdentifier() {
        val raw="""{"title":"Unauthorized","status":401,"access_token":"FAKE_TEST_SECRET","detail":"Bearer FAKE_TEST_SECRET"}"""
        val error=ApiRequestFailure.from(401,raw.byteInputStream())
        assertEquals("Unauthorized",error.publicCode); assertTrue(error.message!!.contains("HTTP 401"))
        assertFalse(error.message!!.contains("FAKE_TEST_SECRET"))
        assertNull(ApiRequestFailure.code("""{"title":"FAKE_TEST_SECRET","error_description":"hidden"}"""))
    }
    @Test fun credentialRevisionRefreshesCommentsWithoutLogout() {
        var reads=0
        val fake=object:CommentDataSource {
            override suspend fun read(article:Article,target:CommentTarget,page:Int):CommentPage { reads++; return CommentPage(emptyList(),false,"测试") }
            override suspend fun post(target:CommentTarget,text:String) = error("Not used")
        }
        val model=CommentsViewModel(Article("https://www.cnblogs.com/cmt/p/1.html","","","",""),fake)
        try { model.start(true,1); idle(); model.start(true,1); idle(); assertEquals(1,reads)
            model.start(true,2); idle(); assertEquals(2,reads) }
        finally { model.viewModelScope.cancel() }
    }
    @Test fun profileFailureDoesNotInventTotalFromLoadedPosts() {
        val fake=object:AuthorDataSource {
            override suspend fun profile(author:AuthorRef):AuthorProfile = throw ApiRequestFailure(401,"Unauthorized")
            override suspend fun posts(author:AuthorRef,page:Int)=listOf(Article("https://www.cnblogs.com/cmt/p/1.html","标题","昵称","",""))
        }
        val model=AuthorViewModel(AuthorRef("cmt","昵称",null),fake)
        try { idle(); assertEquals(1,model.state.value.articles.size); assertNull(model.state.value.profile)
            assertTrue(model.state.value.profileError!!.contains("HTTP 401"))
            assertTrue(authorProfilePresentation(model.state.value.profile,false,model.state.value.profileError).count.contains("未能获取")) }
        finally { model.viewModelScope.cancel() }
    }
}
