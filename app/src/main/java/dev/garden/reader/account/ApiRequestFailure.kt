package dev.garden.reader.account

import java.io.IOException
import java.io.InputStream
import org.json.JSONObject

/** Only fixed public error identifiers escape this parser; never expose raw bodies/headers. */
class ApiRequestFailure(val status: Int, val publicCode: String?) : IOException(buildString {
    append(when (status) {
        401 -> "接口未接受当前授权；登录成功不代表该接口支持 PAT"
        403 -> "接口拒绝访问；当前授权可能没有此接口权限"
        404 -> "接口或资源未找到"
        429 -> "请求过于频繁，请稍后手动重试"
        else -> "博客园接口请求失败"
    })
    append("（HTTP $status")
    if (publicCode != null) append(" · $publicCode")
    append("）")
}) {
    companion object {
        private val allowed = setOf("Unauthorized", "Forbidden", "Not Found", "Too Many Requests",
            "invalid_token", "insufficient_scope", "invalid_request", "invalid_client", "unauthorized_client")
        fun code(raw: String): String? = runCatching {
            val json = JSONObject(raw)
            listOf("error", "title").firstNotNullOfOrNull { (json.opt(it) as? String)?.takeIf(allowed::contains) }
        }.getOrNull()
        fun from(status: Int, input: InputStream?): ApiRequestFailure {
            val raw = runCatching { input?.use {
                val out = java.io.ByteArrayOutputStream(); val bytes = ByteArray(512)
                while (out.size() < 4096) {
                    val n = it.read(bytes, 0, minOf(bytes.size, 4096 - out.size()))
                    if (n < 0) break
                    out.write(bytes, 0, n)
                }
                out.toString("UTF-8")
            } }.getOrNull()
            return ApiRequestFailure(status, raw?.let(::code))
        }
    }
}
