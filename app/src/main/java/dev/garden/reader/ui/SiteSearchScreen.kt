package dev.garden.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.garden.reader.GardenViewModel
import dev.garden.reader.data.Article
import dev.garden.reader.features.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun SiteSearchScreen(news: Boolean, model: GardenViewModel, login: () -> Unit, back: () -> Unit, saved: Boolean = false,
    onAuthor: (Article) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val service = remember { SiteSearch(model.account) }
    val backdrop = rememberGlassBackdrop()
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(if (saved) 0 else if (news) 1 else 0) }
    var titleOnly by remember { mutableStateOf(false) }
    var articles by remember { mutableStateOf(emptyList<Article>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    fun search(next: Boolean) {
        if (saved || input.isBlank() || next && loading) return
        job?.cancel(); generation++; val ticket = generation
        val term = if (next) query else input.trim()
        val index = if (next) page + 1 else 1
        val category = listOf("Blog", "News", "Kb")[tab]
        loading = true; message = null
        if (!next) { articles = emptyList(); query = term; page = 0; more = false }
        job = scope.launch {
            try {
                val result = service.search(term, category == "News", index, category)
                if (generation == ticket) {
                    articles = (if (next) articles + result else result).distinctBy { it.url }
                    page = index; more = result.isNotEmpty()
                    if (articles.isEmpty()) message = "没有找到匹配结果"
                }
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { if (ticket == generation) message = e.message ?: "站内搜索失败" }
            finally { if (ticket == generation) loading = false }
        }
    }
    val shown = if (saved) BookmarkSearch.filter(state.bookmarks, input, tab, titleOnly)
        else if (titleOnly && query.isNotBlank()) articles.filter { it.title.contains(query, true) } else articles
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize().recordGlassBackdrop(backdrop).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 152.dp, bottom = bottom + 75.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (titleOnly) item { Text("仅显示标题包含关键词的结果", style = MaterialTheme.typography.bodySmall) }
            if (saved) item { Text("本机收藏 · ${shown.size} 篇文章和新闻", style = MaterialTheme.typography.bodySmall) }
            if (!saved && !model.account.state.value.loggedIn) item {
                GlassPanel(Modifier.fillMaxWidth(), backdrop) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("官方站内搜索需要 API 授权；本地收藏搜索不需要登录。")
                        GlassButton("原生授权", login, backdrop = backdrop)
                    }
                }
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            if (shown.isEmpty() && !loading && message == null) item {
                Column(Modifier.fillMaxWidth().padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Icon(Icons.Outlined.Inventory2, null, Modifier.size(64.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (saved && input.isBlank()) "还没有收藏，文章与新闻都可以收藏" else if (saved) "没有匹配的收藏" else "输入关键词，搜索整个博客园")
                }
            }
            items(shown, key = { it.url }) { article ->
                ArticleGlassCard(article, state.bookmarks.any { it.url == article.url }, backdrop,
                    { model.open(article) }, { model.bookmark(article) }, { onAuthor(article) })
            }
            if (loading) item { CircularProgressIndicator(Modifier.size(24.dp)) }
            if (!saved && more) item { GlassButton("下一页", { search(true) }, backdrop = backdrop, enabled = !loading) }
        }
        EdgeVeil(backdrop, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 165.dp))
        EdgeVeil(backdrop, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom + 80.dp))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPanel(Modifier.fillMaxWidth(), backdrop) {
                Row(Modifier.fillMaxWidth().padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", back, backdrop = backdrop)
                    BasicTextField(input, { input = it.take(100) }, Modifier.weight(1f).padding(horizontal = 10.dp),
                        singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search(false) }),
                        decorationBox = { inner -> Box { if (input.isBlank()) Text(if (saved) "搜索自己的收藏" else "请输入关键字搜索",
                            color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
                    GlassIconButton(Icons.Outlined.FilterList, "只搜索标题", { titleOnly = !titleOnly }, backdrop = backdrop, selected = titleOnly)
                    GlassIconButton(Icons.Outlined.Search, "执行搜索", { search(false) }, backdrop = backdrop)
                }
            }
            GlassSegments(if (saved) listOf("全部", "文章", "新闻") else listOf("博客", "新闻", "知识库"), tab, { index ->
                job?.cancel(); generation++; loading = false; tab = index; articles = emptyList(); more = false; page = 0; message = null
                if (input.isNotBlank() && !saved) search(false)
            }, backdrop, Modifier.fillMaxWidth())
        }
    }
}
