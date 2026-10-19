package dev.garden.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.Article
import dev.garden.reader.features.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CommentsSheet(article: Article, account: GardenAccountSession, backdrop: GlassBackdrop,
    onLogin: () -> Unit, close: () -> Unit) {
    val target = remember(article.url) { CommentTarget.from(article.url) }
    val service = remember(account) { Comments(account) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(emptyList<NativeComment>()) }
    var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    fun load(reset: Boolean) {
        if (target == null || loading || posting) return
        loading = true; message = null
        scope.launch {
            try {
                val next = if (reset) 1 else page + 1
                val batch = service.list(target, next)
                list = (if (reset) batch else list + batch).distinctBy { it.id }
                page = next; more = batch.size == 20
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { message = e.message ?: "评论读取失败" }
            finally { loading = false }
        }
    }
    LaunchedEffect(target) { if (account.state.value.loggedIn) load(true) }
    GlassSheet("评论 · ${article.comments ?: "—"}", backdrop, { if (!posting) close() }) {
        Column(Modifier.fillMaxWidth().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (target == null) Text("该类型文章暂未提供可用的官方评论接口。")
            else if (!account.state.value.loggedIn) {
                Text("使用博客园官方 API 读取与发布评论，需要先授权。")
                GlassButton("原生授权", { close(); onLogin() }, backdrop = backdrop)
            } else {
                message?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list, key = { it.id }) { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NativeAvatar(item.avatar, item.author, Modifier.size(30.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(item.author, style = MaterialTheme.typography.labelLarge)
                                Text(item.date, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.body, fontSize = 14.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                    if (list.isEmpty() && !loading && message == null) item { Text("暂无评论，欢迎交流。") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(if (loading) "正在读取…" else "刷新", { load(true) }, backdrop = backdrop, enabled = !loading && !posting)
                    if (more && page > 0) GlassButton("更多评论", { load(false) }, backdrop = backdrop, enabled = !loading && !posting)
                }
                GlassPanel(Modifier.fillMaxWidth(), backdrop) {
                    BasicTextField(draft, { if (!posting && it.length <= 4000) draft = it },
                        Modifier.fillMaxWidth().heightIn(min = 68.dp, max = 128.dp).padding(16.dp), enabled = !posting,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner -> Box { if (draft.isBlank()) Text("写评论…", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
                }
                Text("点击发布会以你的账号公开发送；网络异常时请先刷新确认，避免重复提交。", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                GlassButton(if (posting) "正在发布，请勿重复提交…" else "发布评论", {
                    if (!posting && draft.isNotBlank()) {
                        posting = true; message = null; val submitted = draft
                        scope.launch {
                            try { service.post(target, submitted); draft = ""; message = "服务器已接受评论，刷新可查看。" }
                            catch (c: CancellationException) { throw c }
                            catch (e: Exception) { message = e.message ?: "未确认是否发布，请先刷新评论" }
                            finally { posting = false }
                        }
                    }
                }, Modifier.fillMaxWidth(), backdrop, selected = true, enabled = !posting && !loading && draft.isNotBlank())
            }
        }
    }
}
