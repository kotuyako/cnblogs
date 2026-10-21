package dev.garden.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.garden.reader.AuthorViewModel
import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.Article
import dev.garden.reader.features.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(author: AuthorRef, account: GardenAccountSession, open: (Article) -> Unit,
    back: () -> Unit, onLogin: () -> Unit) {
    val factory = remember(author.blog, account) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthorViewModel(author, AuthorRepository(account)) as T
    } }
    val model: AuthorViewModel = viewModel(key = "author:${author.blog.lowercase()}", factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val identity by account.state.collectAsStateWithLifecycle()
    var previousLogin by remember { mutableStateOf(identity.loggedIn) }
    LaunchedEffect(identity.loggedIn) {
        if (identity.loggedIn && !previousLogin) model.refresh()
        previousLogin = identity.loggedIn
    }
    val scroll = rememberLazyListState()
    val backdrop = rememberGlassBackdrop(); val ambient = rememberGlassBackdrop()
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val name = state.articles.firstOrNull { it.author.isNotBlank() }?.author ?: author.name
    val avatar = state.articles.firstNotNullOfOrNull { it.avatar } ?: author.avatar
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LiquidBackdrop(Modifier.fillMaxSize().recordGlassBackdrop(ambient))
        PullToRefreshBox(state.refreshing, model::refresh, Modifier.fillMaxSize()) {
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().recordGlassBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 90.dp, bottom = bottom + 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "profile") { GlassPanel(Modifier.fillMaxWidth(), ambient) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            NativeAvatar(avatar, name, Modifier.size(64.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name.ifBlank { "昵称暂未提供" }, style = MaterialTheme.typography.headlineSmall)
                                Text(author.blog, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(state.profile?.title ?: "博客标题暂未提供", style = MaterialTheme.typography.titleLarge)
                        Text(state.profile?.subtitle ?: "简介暂未提供", style = MaterialTheme.typography.bodyMedium)
                        Text(state.profile?.postCount?.let { "共 $it 篇文章" } ?: "文章总数暂未提供")
                        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                } }
                if (!identity.loggedIn) item(key = "authorize") {
                    GlassPanel(Modifier.fillMaxWidth(), ambient) { Column(Modifier.padding(20.dp)) {
                        Text("作者官方接口需要 Bearer 授权，请使用现有原生授权。")
                        GlassButton("原生授权", onLogin, backdrop = ambient)
                    } }
                }
                state.profileError?.let { error -> item(key = "profileError") {
                    Text("资料读取失败：$error", color = MaterialTheme.colorScheme.error)
                    GlassButton("重试资料及首页", model::refresh, backdrop = ambient, enabled = !state.refreshing)
                } }
                state.error?.let { error -> item(key = "postError") {
                    Text("文章读取失败：$error", color = MaterialTheme.colorScheme.error)
                    GlassButton("重试文章", { if ((state.failedPage ?: 1) > 1) model.more() else model.refresh() },
                        backdrop = ambient, enabled = !state.refreshing && !state.loadingMore)
                } }
                if (!state.refreshing && state.error == null && state.page > 0 && state.articles.isEmpty()) item(key = "empty") {
                    Text("这位作者暂无公开文章", Modifier.padding(24.dp))
                }
                items(state.articles, key = { it.url }) { article -> ArticleGlassCard(article, false, ambient, { open(article) }, null) }
                if (state.loadingMore) item(key = "loadingMore") { CircularProgressIndicator(Modifier.size(24.dp)) }
                if (state.hasMore) item(key = "more") {
                    GlassButton("加载下一页", model::more, backdrop = ambient, enabled = !state.refreshing && !state.loadingMore)
                }
            }
        }
        EdgeVeil(backdrop, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 90.dp))
        EdgeVeil(backdrop, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom + 100.dp))
        Row(Modifier.statusBarsPadding().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", back, backdrop = backdrop)
            Text("作者主页", style = MaterialTheme.typography.titleLarge)
        }
    }
}
