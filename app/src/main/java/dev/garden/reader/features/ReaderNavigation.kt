package dev.garden.reader.features

import dev.garden.reader.data.NativeBlock
import java.net.URI
import java.net.URLDecoder

/** Shared by the native in-body TOC and the directory sheet. Never guesses a heading. */
object ReaderNavigation {
    const val BODY_PREFIX_ITEMS = 1
    fun blockItem(index: Int) = BODY_PREFIX_ITEMS + index

    fun target(link: String, articleUrl: String, blocks: List<NativeBlock>): Int? = runCatching {
        val base = URI(articleUrl)
        val resolved = base.resolve(link)
        if (resolved.scheme != base.scheme || resolved.host != base.host || resolved.path != base.path || resolved.query != base.query) return null
        val fragment = resolved.rawFragment?.takeIf { it.isNotBlank() } ?: return null
        val key = URLDecoder.decode(fragment.replace("+", "%2B"), "UTF-8")
        blocks.indexOfFirst { it.anchor == key }.takeIf { it >= 0 }?.let(::blockItem)
    }.getOrNull()
}
