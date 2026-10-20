package dev.garden.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.garden.reader.data.Article
import dev.garden.reader.features.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AuthorScreen(author: AuthorRef, open: (Article) -> Unit, back: () -> Unit) {
    val backdrop = rememberGlassBackdrop(); val scope = rememberCoroutineScope()
    val ambient = rememberGlassBackdrop()
    var articles by remember(author.blog) { mutableStateOf(emptyList<Article>()) }
    var loading by remember { mutableStateOf(false) }; var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    fun load(reset: Boolean) {
        if (loading) return
        loading = true; error = null
        scope.launch {
            try { val index = if (reset) 1 else page + 1; val result = Authors().page(author, index)
                articles = (if (reset) result.articles else articles + result.articles).distinctBy { it.url }; page = index; more = result.next != null
            } catch (c: CancellationException) { throw c } catch (e: Exception) { error = e.message }
            finally { loading = false }
        }
    }
    LaunchedEffect(author.blog) { load(true) }
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LiquidBackdrop(Modifier.fillMaxSize().recordGlassBackdrop(ambient))
        LazyColumn(Modifier.fillMaxSize().recordGlassBackdrop(backdrop).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 90.dp, bottom = bottom + 80.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { GlassPanel(Modifier.fillMaxWidth(), ambient) { Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) { NativeAvatar(author.avatar, author.name, Modifier.size(64.dp))
                Column { Text(author.name, style = MaterialTheme.typography.headlineSmall); Text("${author.blog} · 公开博文", style = MaterialTheme.typography.bodySmall) } } } }
            error?.let { item { Text(it); GlassButton("重试", { load(true) }, backdrop = ambient) } }
            items(articles, key = { it.url }) { article -> ArticleGlassCard(article, false, ambient, { open(article) }, null) }
            if (loading) item { CircularProgressIndicator(Modifier.size(24.dp)) }
            if (more) item { GlassButton("加载下一页", { load(false) }, backdrop = ambient, enabled = !loading) }
        }
        EdgeVeil(backdrop, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 90.dp))
        EdgeVeil(backdrop, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom + 100.dp))
        Row(Modifier.statusBarsPadding().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", back, backdrop = backdrop); Text("作者", style = MaterialTheme.typography.titleLarge)
        }
    }
}
