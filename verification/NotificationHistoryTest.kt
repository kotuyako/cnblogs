package dev.garden.reader.features

import android.content.Context
import dev.garden.reader.data.Article
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Deterministic storage/state tests. Articles are explicit fixtures, never live notification rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@ConscryptMode(ConscryptMode.Mode.OFF)
class NotificationHistoryTest {
    private lateinit var context: Context
    private fun article(id: Int) = Article("https://www.cnblogs.com/test/p/$id.html", "测试文章 $id", "测试作者", "", "")

    @Before fun clearPreferences() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("article_notifications", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun baselineAndReadFlagsPersistAcrossStoreRecreation() {
        val first = NotificationHistory(context)
        first.enabled(true)
        val baseline = first.accept(first.beginCheck()!!, listOf(article(1)), 10)!!
        assertTrue(baseline.baseline)
        assertTrue(first.state.value.records.isEmpty())
        first.accept(first.beginCheck()!!, listOf(article(2), article(1)), 20)
        first.markRead(article(2).url)
        first.markNotified(setOf(article(2).url))
        val restored = NotificationHistory(context)
        assertTrue(restored.state.value.enabled)
        assertTrue(restored.state.value.initialized)
        assertEquals(20L, restored.state.value.lastCheckedAt)
        assertEquals(1, restored.state.value.records.size)
        assertTrue(restored.state.value.records.single().read)
        assertTrue(restored.state.value.records.single().notified)
        assertTrue(restored.accept(restored.beginCheck()!!, listOf(article(2), article(1)), 30)!!.fresh.isEmpty())
    }

    @Test fun redundantEnableDoesNotResetBaseline() {
        val store = NotificationHistory(context)
        store.enabled(true)
        store.accept(store.beginCheck()!!, listOf(article(1)), 10)
        store.enabled(true)
        assertEquals(listOf(article(2)), store.accept(store.beginCheck()!!, listOf(article(2), article(1)), 20)!!.fresh)
    }

    @Test fun disableStopsAnInflightResponseAndKeepsHistory() {
        val store = NotificationHistory(context)
        store.enabled(true)
        store.accept(store.beginCheck()!!, listOf(article(1)), 10)
        store.accept(store.beginCheck()!!, listOf(article(2)), 20)
        val inflight = store.beginCheck()!!
        store.enabled(false)
        assertNull(store.accept(inflight, listOf(article(3)), 30))
        assertFalse(store.state.value.checking)
        assertEquals(listOf(article(2)), store.state.value.records.map { it.article })
    }

    @Test fun reenableDiscardsOldInflightAndCreatesNewBaselineWithoutOldSpam() {
        val store = NotificationHistory(context)
        store.enabled(true)
        val old = store.beginCheck()!!
        store.enabled(false)
        store.enabled(true)
        assertNull(store.accept(old, listOf(article(3)), 10))
        assertTrue(store.accept(store.beginCheck()!!, listOf(article(4)), 20)!!.baseline)
        assertTrue(store.state.value.records.isEmpty())
    }

    @Test fun failureOrCancellationDoesNotInventBaselineOrEraseRecords() {
        val store = NotificationHistory(context)
        store.enabled(true)
        val failed = store.beginCheck()!!
        store.failed(failed, "首页返回 HTTP 503")
        assertFalse(store.state.value.initialized)
        assertEquals("首页返回 HTTP 503", store.state.value.lastError)
        store.cancelled(store.beginCheck()!!)
        assertFalse(store.state.value.checking)
        assertTrue(store.accept(store.beginCheck()!!, listOf(article(1)), 10)!!.baseline)
    }

    @Test fun unreadUndeliveredRecordSurvivesRestartForPermissionOffCase() {
        val store = NotificationHistory(context)
        store.enabled(true)
        store.accept(store.beginCheck()!!, listOf(article(1)), 10)
        store.accept(store.beginCheck()!!, listOf(article(2), article(1)), 20)
        // No markNotified call: system permission was denied/revoked, but the article was discovered.
        val notice = NotificationHistory(context).state.value.records.single()
        assertEquals(article(2), notice.article)
        assertFalse(notice.read)
        assertFalse(notice.notified)
    }

    @Test fun oldVersionSeenPreferenceMigratesWithoutDuplicateAnnouncements() {
        context.getSharedPreferences("article_notifications", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true).putStringSet("seen", setOf(article(1).url)).commit()
        val store = NotificationHistory(context)
        val delta = store.accept(store.beginCheck()!!, listOf(article(2), article(1)), 20)!!
        assertFalse(delta.baseline)
        assertEquals(listOf(article(2)), delta.fresh)
        assertEquals(1, NotificationHistory(context).state.value.records.size)
    }
}
