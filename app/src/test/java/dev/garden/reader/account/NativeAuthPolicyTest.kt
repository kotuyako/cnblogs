package dev.garden.reader.account

import org.junit.Assert.*
import org.junit.Test

class NativeAuthPolicyTest {
    @Test fun acceptsCompletePat() { assertTrue(NativeAuthPolicy.validToken("a".repeat(64))) }
    @Test fun rejectsPasswordAndShortToken() { assertFalse(NativeAuthPolicy.validToken("password123")) }
    @Test fun rejectsHeaderInjection() { assertFalse(NativeAuthPolicy.validToken("a".repeat(62) + "\r\n")) }
    @Test fun rejectsWhitespace() { assertFalse(NativeAuthPolicy.validToken("a".repeat(63) + " ")) }
    @Test fun identityRequiresBothFields() {
        assertTrue(NativeAuthPolicy.validIdentity("user-id", "园友"))
        assertFalse(NativeAuthPolicy.validIdentity("", "园友"))
        assertFalse(NativeAuthPolicy.validIdentity("user-id", ""))
        assertFalse(NativeAuthPolicy.validIdentity("null", "null"))
    }
    @Test fun mapsOnlyOfficialArticleHostsToApi() {
        assertEquals("/newsitems/123/body", NativeAuthPolicy.articlePath("https://news.cnblogs.com/n/123/"))
        assertEquals("/blogposts/456/body", NativeAuthPolicy.articlePath("https://www.cnblogs.com/a/p/456.html"))
        assertEquals("/blogposts/789/body", NativeAuthPolicy.articlePath("https://www.cnblogs.com/a/p/789/pretty-title"))
        assertEquals("/kbarticles/42/body", NativeAuthPolicy.articlePath("https://kb.cnblogs.com/page/42/"))
        assertNull(NativeAuthPolicy.articlePath("https://news.cnblogs.com.evil.test/n/123/"))
        assertNull(NativeAuthPolicy.articlePath("https://u@news.cnblogs.com/n/123/"))
        assertNull(NativeAuthPolicy.articlePath("http://news.cnblogs.com/n/123/"))
        assertNull(NativeAuthPolicy.articlePath("https://news.cnblogs.com:8443/n/123/"))
    }
}
