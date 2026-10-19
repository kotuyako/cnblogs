package dev.garden.reader.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import dev.garden.reader.data.ContentParser
import dev.garden.reader.data.WebContentParser
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AccountState(val loggedIn: Boolean = false, val displayName: String? = null,
    val checking: Boolean = false, val message: String? = null, val avatar: String? = null,
    val gardenNumber: String? = null, val joined: String? = null, val beans: String? = null,
    val membership: String? = null, val seniority: String? = null)

/** Official PAT protocol. No browser cookies or guesses based on page markup. */
class GardenAccountSession(context: Context) {
    private val preferences = context.getSharedPreferences("native_account", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()

    suspend fun login(raw: String) = mutex.withLock {
        val token = raw.trim()
        if (!NativeAuthPolicy.validToken(token)) {
            mutable.value = AccountState(message = "请输入完整的 64 位个人访问令牌，不是账号密码。")
        } else verify(token, persist = true)
    }

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        try {
            val token = withContext(Dispatchers.IO) { runCatching { readToken() }.getOrNull() }
            if (token != null) verify(token, persist = false)
        } finally { mutex.unlock() }
    }

    private suspend fun verify(token: String, persist: Boolean) {
        mutable.value = mutable.value.copy(checking = true, message = null)
        try {
            val identity = withContext(Dispatchers.IO) {
                val connection = URI("https://api.cnblogs.com/api/users/v2").toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000; connection.readTimeout = 15_000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Authorization-Type", "pat")
                    connection.setRequestProperty("Accept", "application/json")
                    val status = connection.responseCode
                    check(status !in listOf(401,403)) { "令牌无效、权限不足或已过期，请重新创建。" }
                    check(status == 200) { "验证暂未完成（HTTP $status），请稍后重试。" }
                    val body = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            check(output.size() + count <= 128 * 1024) { "账号响应异常" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    val user = JSONObject(body)
                    check(NativeAuthPolicy.validIdentity(user.optString("userId"), user.optString("displayName"))) {
                        "服务器未返回有效用户身份，未建立登录状态。"
                    }
                    user
                } finally { connection.disconnect() }
            }
            if (persist) withContext(Dispatchers.IO) { saveToken(token) }
            fun field(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
                identity.optString(name).takeIf { it.isNotBlank() && it != "null" }
            }
            mutable.value = AccountState(true, identity.getString("displayName").take(100),
                message = "已通过博客园 API 验证身份",
                avatar = WebContentParser.imageUrl(field("avatar", "face").orEmpty(), "https://api.cnblogs.com/"),
                gardenNumber = field("spaceUserId", "userNo"), joined = field("joinTime", "dateAdded", "registerTime"),
                beans = field("beans", "beanCount"), membership = field("membership", "vipStatus"), seniority = field("seniority"))
            // The legacy identity endpoint documents SpaceUserId and Seniority. Optional enrichment
            // must never turn a successfully verified modern identity into a login failure.
            runCatching {
                val legacy = JSONObject(apiRequest("/users"))
                mutable.value = mutable.value.copy(
                    gardenNumber = mutable.value.gardenNumber ?: legacy.optString("SpaceUserId").takeIf { it.isNotBlank() && it != "0" },
                    seniority = mutable.value.seniority ?: legacy.optString("Seniority").takeIf { it.isNotBlank() },
                    avatar = mutable.value.avatar ?: WebContentParser.imageUrl(legacy.optString("Avatar"), "https://api.cnblogs.com/"))
            }.onFailure { if (it is CancellationException) throw it }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutable.value = AccountState(message = error.message ?: "连接失败，请检查网络后重试。")
        } finally { mutable.value = mutable.value.copy(checking = false) }
    }

    suspend fun logout() = mutex.withLock {
        withContext(Dispatchers.IO) { check(preferences.edit().clear().commit()) }
        mutable.value = AccountState(message = "已退出，设备上的授权令牌已清除")
    }

    /** Exact official host only; redirects disabled so bearer credentials never follow a Location. */
    suspend fun apiRequest(path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        check(path.startsWith("/") && !path.startsWith("//") && !path.contains("..") && !path.contains('#')) { "无效 API 路径" }
        val token = readToken() ?: error("请先在我的页面完成原生授权")
        val connection = URI("https://api.cnblogs.com/api$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Authorization-Type", "pat")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            check(status !in listOf(401, 403)) { "授权无效或没有此接口权限（HTTP $status），请检查 PAT 权限；API 授权不等于网站会话。" }
            check(status in 200..299) { "博客园接口请求未成功（HTTP $status），未确认评论已发布。" }
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val bytes = ByteArray(8192)
                while (true) { val n = input.read(bytes); if (n < 0) break
                    check(out.size() + n <= 2 * 1024 * 1024) { "接口响应过大" }; out.write(bytes, 0, n) }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    /** API-only fallback for bodies that the public website cannot provide. Never cached on disk. */
    suspend fun articleBody(url: String): String? = withContext(Dispatchers.IO) {
        if (!state.value.loggedIn) return@withContext null
        val path = NativeAuthPolicy.articlePath(url) ?: return@withContext null
        val token = readToken() ?: return@withContext null
        val connection = URI("https://api.cnblogs.com/api$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Authorization-Type", "pat")
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode == 200) { "博客园 API 未允许读取此正文（HTTP ${connection.responseCode}）。授权不等于网页会员权限。" }
            val text = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val bytes = ByteArray(8192)
                while (true) {
                    val count = input.read(bytes); if (count < 0) break
                    check(out.size() + count <= 4 * 1024 * 1024) { "正文过大" }
                    out.write(bytes, 0, count)
                }
                out.toString("UTF-8")
            }
            val body = JSONTokener(text).nextValue() as? String ?: error("API 没有返回可阅读的正文")
            check(body.isNotBlank()) { "正文为空" }
            ContentParser.cleanBody(body, url)
        } finally { connection.disconnect() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("garden_pat", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("garden_pat", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        check(preferences.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(cipher.doFinal(token.toByteArray()), Base64.NO_WRAP)).commit())
    }
    private fun readToken(): String? {
        val text = preferences.getString("token", null) ?: return null
        val iv = preferences.getString("iv", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(text, Base64.NO_WRAP)), Charsets.UTF_8)
    }
}
