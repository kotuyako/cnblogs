package dev.garden.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.garden.reader.CommentsViewModel
import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.Article
import dev.garden.reader.features.*

@Composable
private fun commentsModel(article: Article, account: GardenAccountSession): CommentsViewModel {
    val factory = remember(article.url, article.blogApp, account) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CommentsViewModel(article, Comments(account)) as T
    } }
    val model: CommentsViewModel = viewModel(key = "comments:${article.blogApp}:${article.url}", factory = factory)
    val identity by account.state.collectAsStateWithLifecycle()
    LaunchedEffect(identity.loggedIn) { model.start(identity.loggedIn) }
    return model
}

@Composable
internal fun ArticleCommentsPreview(article: Article, account: GardenAccountSession, open: () -> Unit, login: () -> Unit) {
    val model = commentsModel(article, account)
    val state by model.state.collectAsStateWithLifecycle()
    val identity by account.state.collectAsStateWithLifecycle()
    // No self-sampling: this panel is inside the reader's recorded backdrop.
    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("文章评论", style = MaterialTheme.typography.titleLarge)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); GlassButton("重试评论", model::retry, enabled = !state.loading) }
            if (state.page > 0 && state.items.isEmpty() && !state.loading && state.error == null) Text("暂无评论")
            state.items.take(2).forEach { item ->
                Text("${item.author} · ${item.date}", style = MaterialTheme.typography.labelMedium)
                Text(item.body, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
            }
            GlassButton("查看全部 / 写评论", open)
            if (!identity.loggedIn) GlassButton("登录后读取和发表评论", login)
        }
    }
}

@Composable
internal fun CommentsSheet(article: Article, account: GardenAccountSession, backdrop: GlassBackdrop,
    onLogin: () -> Unit, close: () -> Unit, onAuthor: (AuthorRef) -> Unit = {}) {
    val identity by account.state.collectAsStateWithLifecycle()
    val model = commentsModel(article, account)
    val state by model.state.collectAsStateWithLifecycle()
    val target = remember(article.url, article.blogApp) { CommentTarget.from(article) }
    val content = rememberGlassBackdrop()
    BackHandler(enabled = state.posting) { }
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize().recordGlassBackdrop(content).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 85.dp, bottom = bottom + 60.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(state.source, style = MaterialTheme.typography.bodySmall) }
            if (target == null) item { Text("该文章类型暂无可用评论接口。") }
            state.notice?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            state.error?.let { item {
                Text(it, color = MaterialTheme.colorScheme.error)
                GlassButton("重试失败页", model::retry, backdrop = backdrop, enabled = !state.loading && !state.posting)
            } }
            if (state.loading) item { CircularProgressIndicator(Modifier.size(24.dp)) }
            if (state.page > 0 && state.items.isEmpty() && !state.loading && state.error == null) item { Text("暂无评论，欢迎交流。") }
            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                GlassPanel(Modifier.fillMaxWidth(), backdrop) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val author = item.authorRef()
                            NativeAvatar(item.avatar, item.author, Modifier.size(42.dp),
                                onClick = author?.let { ref -> { onAuthor(ref) } })
                            Column(Modifier.weight(1f)) {
                                Text(item.author, Modifier.clickable(enabled = author != null && !state.posting) { author?.let(onAuthor) },
                                    style = MaterialTheme.typography.titleMedium)
                                Text(item.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("#${index + 1}", fontSize = 11.sp)
                        }
                        Text(item.body, fontSize = 16.sp, lineHeight = 27.sp)
                    }
                }
            }
            if (target != null) item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("刷新评论", model::refresh, backdrop = backdrop, enabled = !state.loading && !state.posting)
                if (state.hasMore) GlassButton("下一页", model::more, backdrop = backdrop, enabled = !state.loading && !state.posting)
            } }
            if (!identity.loggedIn && target != null) item {
                GlassPanel(Modifier.fillMaxWidth(), backdrop) { Column(Modifier.padding(20.dp)) {
                    Text("博文评论读取与发布需要官方授权及对应权限；不会改用网页爬取。")
                    GlassButton("原生登录授权", onLogin, backdrop = backdrop)
                } }
            }
            if (identity.loggedIn && target != null) item {
                GlassPanel(Modifier.fillMaxWidth(), backdrop) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BasicTextField(state.draft, model::draft, Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 180.dp).imePadding(),
                        enabled = !state.posting, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner -> Box { if (state.draft.isBlank()) Text("写评论…", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
                    Text("点击发布会公开发送；发送结果不确定时必须先刷新核对。", fontSize = 11.sp)
                    GlassButton(if (state.posting) "正在发布…" else "发布评论", { model.post(identity.loggedIn) },
                        Modifier.fillMaxWidth(), backdrop, selected = true,
                        enabled = !state.posting && !state.loading && !state.uncertain && state.draft.isNotBlank())
                } }
            }
        }
        EdgeVeil(content, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 90.dp))
        EdgeVeil(content, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom + 70.dp))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回正文", { if (!state.posting) close() }, backdrop = content)
            Text("评论", style = MaterialTheme.typography.titleLarge)
        }
    }
}
