package dev.garden.reader.ui

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.Article
import dev.garden.reader.features.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CommentsSheet(article: Article, account: GardenAccountSession, backdrop: GlassBackdrop,
    onLogin: () -> Unit, close: () -> Unit) {
    val identity by account.state.collectAsStateWithLifecycle()
    val target = remember(article.url) { CommentTarget.from(article.url) }
    val service = remember(account) { Comments(account) }
    val content = rememberGlassBackdrop()
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(emptyList<NativeComment>()) }
    var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    BackHandler(enabled = posting) { }
    var draft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf("读取公开评论，必要时尝试已授权 API") }
    fun load(reset: Boolean) {
        if (target == null || loading || posting) return
        loading = true; message = null
        scope.launch {
            try {
                val next = if (reset) 1 else page + 1
                val batch = service.read(article, target, next)
                val oldIds = list.map { it.id }.toSet()
                list = (if (reset) batch.items else list + batch.items).distinctBy { it.id }
                page = next; more = batch.hasMore && (reset || batch.items.any { it.id !in oldIds }); source = batch.source
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { message = e.message ?: "评论读取失败" }
            finally { loading = false }
        }
    }
    LaunchedEffect(target, identity.loggedIn) { load(true) }
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize().recordGlassBackdrop(content).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 85.dp, bottom = bottom + 60.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(source, style = MaterialTheme.typography.bodySmall) }
            if (target == null) item { Text("该文章类型暂无可用评论接口。") }
            message?.let { item { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) } }
            if (loading) item { CircularProgressIndicator(Modifier.size(24.dp)) }
            if (list.isEmpty() && !loading && message == null && target != null) item { Text("暂无评论，欢迎交流。") }
            itemsIndexed(list, key = { _, item -> item.id }) { index, item ->
                GlassPanel(Modifier.fillMaxWidth(), backdrop) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NativeAvatar(item.avatar, item.author, Modifier.size(42.dp))
                            Column(Modifier.weight(1f)) { Text(item.author, style = MaterialTheme.typography.titleMedium)
                                Text(item.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text("#${index + 1}", fontSize = 11.sp)
                        }
                        Text(item.body, fontSize = 16.sp, lineHeight = 27.sp)
                    }
                }
            }
            if (target != null) item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("刷新评论", { load(true) }, backdrop = content, enabled = !loading && !posting)
                if (more) GlassButton("下一页", { load(false) }, backdrop = content, enabled = !loading && !posting)
            } }
            if (!identity.loggedIn && target != null) item {
                GlassPanel(Modifier.fillMaxWidth(), backdrop) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("公开评论无需登录；发表评论或公开读取受限时，需要官方 API 授权。")
                    GlassButton("原生授权", onLogin, backdrop = content)
                } }
            }
            if (identity.loggedIn && target != null) item {
                GlassPanel(Modifier.fillMaxWidth(), backdrop) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BasicTextField(draft, { if (!posting && it.length <= 4000) draft = it },
                        Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 180.dp).imePadding(), enabled = !posting,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner -> Box { if (draft.isBlank()) Text("写评论…", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
                    Text("点击发布会公开发送；网络异常时先刷新确认，避免重复提交。", fontSize = 11.sp)
                    GlassButton(if (posting) "正在发布…" else "发布评论", {
                        if (!posting && draft.isNotBlank()) {
                            posting = true; message = null; val submitted = draft
                            scope.launch {
                                try { service.post(target, submitted); draft = ""; message = "服务器已接受评论，刷新查看。" }
                                catch (c: CancellationException) { throw c }
                                catch (e: Exception) { message = e.message ?: "未确认是否发布，请先刷新" }
                                finally { posting = false }
                            }
                        }
                    }, Modifier.fillMaxWidth(), content, selected = true, enabled = !posting && !loading && draft.isNotBlank())
                } }
            }
        }
        EdgeVeil(content, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 90.dp))
        EdgeVeil(content, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom + 70.dp))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回正文", { if (!posting) close() }, backdrop = content)
            Text("评论", style = MaterialTheme.typography.titleLarge)
        }
    }
}
