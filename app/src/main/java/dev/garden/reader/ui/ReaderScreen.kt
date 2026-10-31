package dev.garden.reader.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import dev.garden.reader.features.ReaderNavigation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garden.reader.GardenState
import dev.garden.reader.GardenViewModel
import dev.garden.reader.data.NativeBlock
import dev.garden.reader.data.NativeDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(state: GardenState, model: GardenViewModel, onBack: () -> Unit, onLogin: () -> Unit,
    onAuthor: (dev.garden.reader.data.Article) -> Unit = {}, visible: Boolean = true,
    onCommentAuthor: (dev.garden.reader.features.AuthorRef) -> Unit = {}) {
    val article = state.selected ?: return
    val account by model.account.state.collectAsStateWithLifecycle()
    val backdrop = rememberGlassBackdrop()
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val chapterOffset = with(density) { -(WindowInsets.statusBars.getTop(this) + 78.dp.roundToPx()) }
    var toc by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    val blocks by produceState<List<NativeBlock>>(emptyList(), state.body) {
        value = withContext(Dispatchers.Default) { state.body?.let(NativeDocument::parse).orEmpty() }
    }
    TrackReading(article.url, visible && !state.bodyLoading && state.bodyError == null && blocks.isNotEmpty() && !toc && !comments)
    LaunchedEffect(scroll) {
        var previous = 0
        snapshotFlow { scroll.firstVisibleItemIndex * 100000 + scroll.firstVisibleItemScrollOffset }.collect { current ->
            if (current < previous - 12 || current < 80) chrome = true
            else if (current > previous + 12) chrome = false
            previous = current
        }
    }
    val bottomInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val topInset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().recordGlassBackdrop(backdrop)
            .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 118.dp + bottomInset)) {
            item(key = "title") {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 78.dp, bottom = 28.dp)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        Text(article.title, Modifier.padding(start = 18.dp, end = 22.dp),
                            fontSize = 27.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NativeAvatar(article.avatar, article.author, Modifier.size(42.dp)) { onAuthor(article) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(article.author, Modifier.clickable { onAuthor(article) }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(article.published.take(25), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ArticleStatistics(article, Modifier.padding(start = 22.dp, top = 16.dp), { comments = true })
                }
            }
            if (state.bodyLoading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            }
            state.bodyError?.let { error -> item(key = "error") {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(error)
                    Text("可读取公开正文，或尝试官方 API 授权。授权不会自动成为网站会员会话。", style = MaterialTheme.typography.bodySmall)
                    GlassButton("重新加载", { model.open(article) }, backdrop = backdrop)
                    if (!account.loggedIn) GlassButton("原生授权后重试", onLogin, backdrop = backdrop)
                }
            } }
            itemsIndexed(blocks, key = { index, _ -> "block-$index" }) { _, block ->
                NativeArticleBlock(block, state.fontSize, Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) { link ->
                    val target = ReaderNavigation.target(link, article.url, blocks)
                    if (target != null) {
                        scope.launch { scroll.animateScrollToItem(target, chapterOffset) }
                    } else if (link.startsWith("#")) {
                        android.widget.Toast.makeText(context, "正文没有提供该章节锚点", android.widget.Toast.LENGTH_SHORT).show()
                    } else dev.garden.reader.data.ContentParser.safeWebUrl(link)?.let { url ->
                        // Explicitly tapped ordinary links use the system; no WebView is embedded.
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                    }
                }
            }
            if (!state.bodyLoading && state.bodyError == null && blocks.isNotEmpty()) item(key = "end") {
                Text("— 全文完 · 内容版权归原作者所有 —", Modifier.fillMaxWidth().padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            if (!state.bodyLoading && state.bodyError == null && blocks.isNotEmpty() &&
                dev.garden.reader.features.CommentTarget.from(article)?.news == false) item(key = "official-comments") {
                ArticleCommentsPreview(article, model.account, { comments = true }, onLogin)
            }
        }
        EdgeVeil(backdrop, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topInset + 112.dp))
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回文章列表", onBack, backdrop = backdrop)
        }
        EdgeVeil(backdrop, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .height(160.dp + bottomInset))
        AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }) {
            GlassPanel(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 560.dp).fillMaxWidth(), backdrop, RoundedCornerShape(40.dp)) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack, backdrop = backdrop)
                    GlassIconButton(Icons.Outlined.ChatBubbleOutline, "查看和发表评论", { comments = true }, backdrop = backdrop)
                    GlassIconButton(if (state.bookmarks.any { it.url == article.url }) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                        "收藏", { model.bookmark(article) }, backdrop = backdrop)
                    GlassIconButton(Icons.Outlined.IosShare, "分享", { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.url}")
                    }, "分享文章")) }, backdrop = backdrop)
                    GlassIconButton(Icons.Outlined.FormatListBulleted, "文章目录", { toc = true }, backdrop = backdrop)
                }
            }
        }
        if (toc) GlassSheet("文章目录", backdrop, { toc = false }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 390.dp)) {
                if (blocks.none { it.kind == "heading" }) item {
                    Text("这篇文章没有章节标题", Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                blocks.forEachIndexed { index, block -> if (block.kind == "heading") item {
                    Text(block.text, Modifier.fillMaxWidth().clickable {
                        toc = false; scope.launch { scroll.animateScrollToItem(ReaderNavigation.blockItem(index), chapterOffset) }
                    }.padding(start = ((block.level - 1) * 10).dp, top = 14.dp, bottom = 14.dp))
                } }
            }
        }
        if (comments) PredictiveGlassLayer(onDismiss = { comments = false }) { back ->
            CommentsSheet(article, model.account, backdrop, onLogin, back, onCommentAuthor)
        }
    }
}

@Composable
internal fun NativeArticleBlock(block: NativeBlock, fontSize: Float, modifier: Modifier, onLink: ((String) -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    if (block.kind == "code") { NativeCodeBlock(block, fontSize, modifier); return }
    if (block.kind == "image") {
        NativeImage(block.source ?: return, block.caption.ifBlank { "文章配图" }, modifier.clip(RoundedCornerShape(5.dp)))
        return
    }
    if (block.kind == "rule") { HorizontalDivider(modifier.padding(vertical = 12.dp)); return }
    val text = buildAnnotatedString {
        block.spans.forEach { span ->
            val style = SpanStyle(fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                color = if (span.link != null) colors.primary else Color.Unspecified,
                background = if (span.code) colors.surfaceContainer else Color.Unspecified)
            if (span.link != null && onLink != null) {
                withLink(LinkAnnotation.Clickable(span.link, TextLinkStyles(style = style), { onLink(span.link) })) { append(span.text) }
            } else withStyle(style) { append(span.text) }
        }
    }
    val code = block.kind in setOf("code", "table")
    val decoration = when {
        code -> Modifier.clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerLow)
            .horizontalScroll(rememberScrollState()).padding(14.dp)
        block.kind == "quote" -> Modifier.background(colors.surfaceContainerLow).padding(16.dp)
        else -> Modifier
    }
    SelectionContainer(modifier) {
        Text(text, decoration, fontSize = when {
            code -> (fontSize - 3).sp
            block.kind == "heading" -> (fontSize + (5 - block.level).coerceAtLeast(0)).sp
            else -> fontSize.sp
        }, lineHeight = (fontSize * 1.95f).sp,
            fontFamily = if (code) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (block.kind == "heading") FontWeight.Bold else FontWeight.Normal,
            color = colors.onSurface, letterSpacing = if (code) 0.sp else 0.3.sp)
    }
}
