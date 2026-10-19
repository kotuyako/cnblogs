package dev.garden.reader.features

import org.junit.Assert.*
import org.junit.Test

class FeaturePoliciesTest {
    @Test fun blogCommentPath() {
        assertEquals("/blogs/alice/posts/123/comments", CommentTarget.from("https://www.cnblogs.com/alice/p/123.html")?.path)
    }
    @Test fun modernPostPath() {
        assertEquals("/blogs/alice/posts/123/comments", CommentTarget.from("https://www.cnblogs.com/alice/p/123/a-title")?.path)
    }
    @Test fun legacyPostPath() {
        assertEquals("/blogs/alice/posts/123/comments", CommentTarget.from("https://www.cnblogs.com/alice/archive/2020/01/02/123.html")?.path)
    }
    @Test fun newsHasOwnCommentEndpoint() {
        assertEquals(CommentTarget("/news/123/comments", true), CommentTarget.from("https://news.cnblogs.com/n/123/"))
    }
    @Test fun cannotLeakCredentialToForeignHost() {
        listOf("http://www.cnblogs.com/a/p/123", "https://www.cnblogs.com.evil.com/a/p/123",
            "https://user@www.cnblogs.com/a/p/123", "https://www.cnblogs.com:444/a/p/123",
            "https://kb.cnblogs.com/page/123/", "https://news.cnblogs.com/n/not-a-number").forEach { assertNull(CommentTarget.from(it)) }
    }
    @Test fun versionsCompareNumerically() {
        assertTrue(ReleaseVersion.newer("v1.10.0", "1.4.0"))
        assertTrue(ReleaseVersion.newer("2.0.0", "1.99.99"))
        assertTrue(ReleaseVersion.newer("1.4.1", "1.4.0"))
    }
    @Test fun noDowngradeOrDuplicate() {
        assertFalse(ReleaseVersion.newer("1.4.0", "1.4.0"))
        assertFalse(ReleaseVersion.newer("1.3.9", "1.4.0"))
    }
    @Test fun malformedVersionNotAnUpgrade() {
        listOf("next", "v1.5.0-beta", "1.5", "99999999999999999999.0.0").forEach { assertFalse(ReleaseVersion.newer(it, "1.4.0")) }
    }
}
