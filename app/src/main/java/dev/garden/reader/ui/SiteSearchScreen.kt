package dev.garden.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.garden.reader.GardenViewModel
import dev.garden.reader.data.Article
import dev.garden.reader.features.SiteSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SiteSearchScreen(news: Boolean, model: GardenViewModel, login: () -> Unit, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val service = remember { SiteSearch(model.account) }
    val backdrop = rememberGlassBackdrop()
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var articles by remember { mutableStateOf(emptyList<Article>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    fun search(next: Boolean) {
        if (loading || input.isBlank()) return
        val term = if (next) query else input.trim()
        val index = if (next) page + 1 else 1
        loading = true; message = null
        if (!next) { articles = emptyList(); query = term; page = 0; more = false }
        scope.launch {
            try {
                val result = service.search(term, news, index)
                articles = (if (next) articles + result else result).distinctBy { it.url }
                page = index; more = result.isNotEmpty()
                if (articles.isEmpty()) message = "没有找到匹配的${if (news) "新闻" else "文章"}"
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { message = e.message ?: "站内搜索失败" }
            finally { loading = false }
        }
    }
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize().recordGlassBackdrop(backdrop).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 80.dp, bottom = bottom + 60.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { GlassInput(input, { input = it.take(100) }, if (news) "搜索博客园新闻" else "搜索博客园文章", onDone = { search(false) }) }
            item { GlassButton(if (loading) "正在搜索…" else "站内搜索", { search(false) }, Modifier.fillMaxWidth(), backdrop, enabled = !loading && input.isNotBlank()) }
            item { Text("通过官方搜索接口查询全部${if (news) "新闻" else "博文"}，需要 API 授权；不会退化成当前列表筛选。", style = MaterialTheme.typography.bodySmall) }
            if (!model.account.state.value.loggedIn) item { GlassButton("原生授权", login, backdrop = backdrop) }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            items(articles, key = { it.url }) { article ->
                GlassPanel(Modifier.fillMaxWidth(), backdrop, onClick = { model.open(article) }) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(article.title, style = MaterialTheme.typography.titleLarge)
                        Text(article.summary, maxLines = 4)
                        Text("${article.author} · ${article.published}", style = MaterialTheme.typography.bodySmall)
                        ArticleStatistics(article)
                    }
                }
            }
            if (more) item { GlassButton("下一页", { search(true) }, backdrop = backdrop, enabled = !loading) }
        }
        EdgeVeil(backdrop, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 85.dp))
        EdgeVeil(backdrop, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom + 60.dp))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", back, backdrop = backdrop)
            Text(if (news) "搜索新闻" else "搜索文章", style = MaterialTheme.typography.titleLarge)
        }
    }
}
