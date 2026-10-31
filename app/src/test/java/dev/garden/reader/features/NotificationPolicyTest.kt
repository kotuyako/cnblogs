package dev.garden.reader.features

import dev.garden.reader.data.Article
import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    private fun article(id: Int) = Article("https://www.cnblogs.com/alice/p/$id.html", "文章 $id", "作者", "", "")

    @Test fun firstSuccessOnlyCreatesBaseline() {
        val delta = NotificationPolicy.compare(null, listOf(article(1), article(2)))
        assertTrue(delta.baseline)
        assertTrue(delta.fresh.isEmpty())
        assertEquals(listOf(article(1).url, article(2).url), delta.seen)
    }

    @Test fun repeatChecksCannotCreateDuplicates() {
        val first = NotificationPolicy.compare(null, listOf(article(1)))
        val second = NotificationPolicy.compare(first.seen, listOf(article(2), article(1), article(2)))
        assertEquals(listOf(article(2)), second.fresh)
        val third = NotificationPolicy.compare(second.seen, listOf(article(2), article(1)))
        assertTrue(third.fresh.isEmpty())
    }

    @Test fun disappearedAndReturnedArticleIsNotNewAgain() {
        val before = listOf(article(1).url, article(2).url)
        val absent = NotificationPolicy.compare(before, listOf(article(2)))
        assertTrue(NotificationPolicy.compare(absent.seen, listOf(article(1), article(2))).fresh.isEmpty())
    }

    @Test fun unreadUndeliveredRecordsSurviveMergeWithoutPermission() {
        val unread = ArticleNotice(article(1), 10)
        val merged = NotificationPolicy.merge(listOf(unread), listOf(article(2)), 20)
        assertEquals(2, merged.size)
        assertEquals(unread, merged.last())
        assertFalse(merged.first().read)
        assertFalse(merged.first().notified)
    }

    @Test fun existingReadAndDeliveredFlagsCannotBeResetByDuplicate() {
        val existing = ArticleNotice(article(1), 10, read = true, notified = true)
        assertEquals(listOf(existing), NotificationPolicy.merge(listOf(existing), listOf(article(1)), 20))
    }

    @Test fun historyIsBoundedWithNewestRecordsFirst() {
        val existing = (1..200).map { ArticleNotice(article(it), it.toLong()) }
        val merged = NotificationPolicy.merge(existing, listOf(article(201), article(202)), 300)
        assertEquals(200, merged.size)
        assertEquals(listOf(article(201), article(202)), merged.take(2).map { it.article })
    }

    @Test fun comparisonKeepsRecentSeenUrlsInBoundedOrder() {
        val delta = NotificationPolicy.compare((1..1100).map { article(it).url }, listOf(article(1200)))
        assertEquals(1000, delta.seen.size)
        assertEquals(article(1200).url, delta.seen.first())
        assertEquals(1, delta.fresh.size)
    }
}
