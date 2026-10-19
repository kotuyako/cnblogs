package dev.garden.reader.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garden.reader.GardenState
import dev.garden.reader.GardenViewModel
import dev.garden.reader.account.AccountState
import dev.garden.reader.data.Article
import dev.garden.reader.data.Channel

@Composable
fun GardenHome(state: GardenState, model: GardenViewModel, account: AccountState, onLogin: () -> Unit,
    modifier: Modifier = Modifier) {
    var destination by rememberSaveable { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf(Channel.HOME) }
    var homeQuery by rememberSaveable { mutableStateOf("") }
    var newsQuery by rememberSaveable { mutableStateOf("") }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    val homeScroll = rememberLazyListState()
    val newsScroll = rememberLazyListState()
    val savedScroll = rememberLazyListState()
    val settingsScroll = rememberLazyListState()
    val ambient = rememberGlassBackdrop()
    val content = rememberGlassBackdrop()
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val density = LocalDensity.current
    val topInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val query = when (destination) { 1 -> newsQuery; 2 -> savedQuery; else -> homeQuery }
    val setQuery: (String) -> Unit = { when (destination) { 1 -> newsQuery = it; 2 -> savedQuery = it; else -> homeQuery = it } }
    val desiredChannel = if (destination == 1) Channel.NEWS else category
    LaunchedEffect(destination, category) {
        if (destination <= 1) model.selectChannel(desiredChannel)
    }
    LaunchedEffect(category) { homeScroll.scrollToItem(0) }
    Box(modifier.fillMaxSize()) {
        LiquidBackdrop(Modifier.fillMaxSize().recordGlassBackdrop(ambient))
        Box(Modifier.fillMaxSize().recordGlassBackdrop(content).background(MaterialTheme.colorScheme.background)) {
            if (destination == 3) {
                SettingsList(account, ambient, settingsScroll, topInset + 82.dp, bottomInset + 116.dp,
                    onLogin, { settingsPage = it })
            } else {
                FeedList(state, model, destination, desiredChannel, query,
                    when (destination) { 1 -> newsScroll; 2 -> savedScroll; else -> homeScroll }, ambient,
                    topInset + if (destination == 0) 135.dp else 83.dp, bottomInset + 120.dp)
            }
        }
        // The veil begins at y=0, including the status bar. Controls float above the same feathered layer.
        EdgeVeil(content, true, Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .height(topInset + if (destination == 0) 164.dp else 112.dp))
        EdgeVeil(content, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomInset + 160.dp))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (destination == 3) {
                    Spacer(Modifier.width(46.dp))
                    Text("我的", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.width(46.dp))
                } else {
                    GlassPanel(Modifier.size(42.dp), content, CircleShape) { CnblogsMark(Modifier.align(Alignment.Center).size(30.dp)) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(when (destination) { 1 -> "博客园新闻"; 2 -> "我的收藏"; else -> "博客园" },
                            fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("第三方原生客户端", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GlassIconButton(Icons.Outlined.Search, when (destination) { 1 -> "搜索新闻"; 2 -> "搜索收藏"; else -> "搜索文章" },
                        { searching = true }, backdrop = content)
                }
            }
            if (destination == 0) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Channel.entries.filter { it != Channel.NEWS }.forEach { channel ->
                    GlassButton(channel.label, { category = channel }, backdrop = content, selected = category == channel)
                }
            }
        }
        if (!keyboard) FloatingNavigation(destination, { destination = it }, content,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 18.dp, vertical = 10.dp).widthIn(max = 560.dp))
        if (searching && destination == 2) SearchGlass(query, setQuery, { searching = false }, content, "搜索本地收藏")
        if (searching && destination <= 1) PredictiveGlassLayer(onDismiss = { searching = false }) { back ->
            SiteSearchScreen(destination == 1, model, onLogin, back)
        }
        settingsPage?.let { page ->
            key(page) { PredictiveGlassLayer(onDismiss = { settingsPage = null }) { back ->
                SettingsDetail(page, state, model, account, onLogin, back)
            } }
        }
    }
}

@Composable
internal fun FloatingNavigation(destination: Int, select: (Int) -> Unit, backdrop: GlassBackdrop, modifier: Modifier) {
    GlassPanel(modifier.fillMaxWidth(), backdrop, RoundedCornerShape(40.dp)) {
        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("首页", "新闻", "收藏", "我的").forEachIndexed { index, label ->
                val current = destination == index
                val color by animateColorAsState(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    label = "navigation tint")
                Box(Modifier.weight(1f).height(59.dp).clip(RoundedCornerShape(32.dp))
                    .background(if (current) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .65f) else Color.Transparent)
                    .semantics { role = Role.Tab; selected = current; contentDescription = label }.clickable { select(index) }) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(when (index) { 0 -> Icons.AutoMirrored.Outlined.MenuBook; 1 -> Icons.Outlined.Newspaper
                            2 -> Icons.Outlined.Bookmarks; else -> Icons.Outlined.PersonOutline }, null, Modifier.size(25.dp), color)
                        Text(label, fontSize = 11.sp, color = color, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(state: GardenState, model: GardenViewModel, destination: Int, channel: Channel, query: String,
    scroll: LazyListState, backdrop: GlassBackdrop, top: androidx.compose.ui.unit.Dp, bottom: androidx.compose.ui.unit.Dp) {
    val saved = destination == 2
    val matches = state.channel == channel
    val source = if (saved) state.bookmarks else if (matches) state.articles else emptyList()
    val articles = remember(source, query) { source.filter {
        query.isBlank() || "${it.title} ${it.author} ${it.summary}".contains(query.trim(), true)
    } }
    val refreshing = !saved && (state.loading || !matches)
    PullToRefreshBox(refreshing, { if (!saved) model.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (query.isNotBlank()) item { Text("“$query” · ${articles.size} 篇匹配", Modifier.padding(6.dp), fontSize = 13.sp) }
            if (!saved && matches && state.error != null) item {
                GlassPanel(Modifier.fillMaxWidth(), backdrop) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("网页内容暂未更新", fontWeight = FontWeight.SemiBold)
                        Text(state.error, fontSize = 13.sp)
                        GlassButton("重新加载", model::refresh, backdrop = backdrop)
                    }
                }
            }
            if (articles.isEmpty()) item {
                GlassPanel(Modifier.fillMaxWidth(), backdrop) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.MenuBook, null, Modifier.size(30.dp))
                        Text(if (refreshing) "正在读取博客园网页…" else if (query.isNotBlank()) "没有匹配的文章" else "这里还没有文章")
                        Text(if (saved) "收藏的文章会留在这里" else "可下拉刷新重试", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(articles, key = { it.url }) { article ->
                ArticleGlassCard(article, state.bookmarks.any { it.url == article.url }, backdrop,
                    { model.open(article) }, { model.bookmark(article) })
            }
            if (!saved && state.hasMore && query.isBlank()) item {
                GlassButton(if (state.loadingMore) "正在读取下一页…" else "加载下一页", model::more,
                    Modifier.fillMaxWidth(), backdrop, enabled = !state.loading && !state.loadingMore)
            }
            if (articles.isNotEmpty()) item {
                Text(if (saved) "收藏保存在当前设备" else "内容来自博客园网页 · ${channel.label}", Modifier.padding(8.dp),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ArticleGlassCard(article: Article, saved: Boolean, backdrop: GlassBackdrop, open: () -> Unit, bookmark: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), backdrop, RoundedCornerShape(28.dp), onClick = open) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(article.title, fontSize = 20.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (article.published.isNotBlank()) Text("发布于 ${article.published}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (article.summary.isNotBlank()) Text(article.summary, Modifier.weight(1f), fontSize = 14.sp,
                    lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                article.image?.let { NativeImage(it, null, Modifier.size(94.dp, 76.dp).clip(RoundedCornerShape(12.dp)), crop = true) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NativeAvatar(article.avatar, article.author, Modifier.size(30.dp))
                Text(article.author, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ArticleStatistics(article)
                GlassIconButton(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                    if (saved) "取消收藏" else "收藏", bookmark, Modifier.size(38.dp), backdrop, saved)
            }
        }
    }
}

@Composable
private fun Statistic(icon: ImageVector, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (value >= 10000) "${value / 1000 / 10f}万" else value.toString(), fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun GlassInput(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    password: Boolean = false, focus: FocusRequester? = null, secureInput: Boolean = password, onDone: () -> Unit = {}) {
    GlassPanel(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        BasicTextField(value, onValue, modifier = Modifier.fillMaxWidth().padding(18.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier).semantics { contentDescription = label },
            singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secureInput) androidx.compose.ui.text.input.KeyboardType.Password
                else androidx.compose.ui.text.input.KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone() }), decorationBox = { inner ->
                Box { if (value.isBlank()) Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
            })
    }
}

@Composable
private fun SearchGlass(query: String, setQuery: (String) -> Unit, close: () -> Unit, backdrop: GlassBackdrop, title: String) {
    BackHandler(onBack = close)
    val focus = remember { FocusRequester() }
    val manager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f)).clickable { close() }
        .statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp), contentAlignment = Alignment.TopCenter) {
        GlassPanel(Modifier.fillMaxWidth().padding(top = 12.dp), backdrop, RoundedCornerShape(32.dp), onClick = {}) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                GlassInput(query, setQuery, "输入标题、作者或关键词", focus = focus, onDone = { manager.clearFocus(); close() })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("清除", { setQuery(""); close() }, Modifier.weight(1f), backdrop)
                    GlassButton("查看结果", { manager.clearFocus(); close() }, Modifier.weight(1f), backdrop, selected = true)
                }
            }
        }
    }
}

@Composable
private fun SettingsList(account: AccountState, backdrop: GlassBackdrop, scroll: LazyListState,
    top: androidx.compose.ui.unit.Dp, bottom: androidx.compose.ui.unit.Dp, onLogin: () -> Unit, open: (String) -> Unit) {
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { SectionLabel("账号") }
        item { GlassGroup(backdrop) {
            SettingRow(Icons.Outlined.PersonOutline, account.displayName ?: "登录博客园", "个人资料与授权", {
                if (account.loggedIn) open("账号") else onLogin()
            }, trailing = { NativeAvatar(account.avatar, account.displayName ?: "园", Modifier.size(42.dp)) })
        } }
        item { SectionLabel("应用") }
        item { GlassGroup(backdrop) {
            SettingRow(Icons.Outlined.Tune, "通用", "主题与沉浸显示", { open("通用") }); GroupDivider()
            SettingRow(Icons.Outlined.Palette, "外观", "液态玻璃与莫奈取色", { open("外观") }); GroupDivider()
            SettingRow(Icons.AutoMirrored.Outlined.MenuBook, "阅读", "字号、排版与手势", { open("阅读") }); GroupDivider()
            SettingRow(Icons.Outlined.NotificationsNone, "新文章通知", "后台定时检查与通知权限", { open("通知") }); GroupDivider()
            SettingRow(Icons.Outlined.SystemUpdate, "检查更新", "当前版本 1.4.0", { open("检查更新") }); GroupDivider()
            SettingRow(Icons.Outlined.Info, "关于", "博客园 1.4.0 · 非官方客户端", { open("关于") })
        } }
    }
}

@Composable
private fun SettingsDetail(page: String, state: GardenState, model: GardenViewModel, account: AccountState,
    onLogin: () -> Unit, onBack: () -> Unit) {
    val ambient = rememberGlassBackdrop(); val content = rememberGlassBackdrop()
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var confirmLogout by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LiquidBackdrop(Modifier.fillMaxSize().recordGlassBackdrop(ambient))
        LazyColumn(Modifier.fillMaxSize().recordGlassBackdrop(content).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 110.dp, bottom = bottom + 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item { Text(page, Modifier.padding(start = 4.dp, top = 12.dp, bottom = 14.dp), fontSize = 36.sp, fontWeight = FontWeight.Bold) }
            if (page in listOf("通用", "外观")) {
                item { SectionLabel("外观") }
                item { GlassGroup(ambient) {
                    SettingRow(Icons.Outlined.Contrast, "主题", "跟随系统会自动切换浅色与深色主题。", {})
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "系统", "light" to "浅色", "dark" to "深色").forEach { (key, label) ->
                            GlassButton(label, { model.theme(key) }, Modifier.weight(1f), ambient, selected = state.theme == key)
                        }
                    }
                    GroupDivider()
                    SettingRow(Icons.Outlined.Palette, "莫奈取色", if (Build.VERSION.SDK_INT >= 31) "使用系统壁纸动态配色" else "当前系统使用默认配色",
                        { model.dynamicColor(!state.dynamicColor) }, trailing = { GlassToggle(state.dynamicColor) { model.dynamicColor(!state.dynamicColor) } })
                } }
                item { SectionLabel("显示") }
                item { GlassGroup(ambient) {
                    SettingRow(Icons.Outlined.BlurOn, "液态玻璃", "所有页面统一使用玻璃材质；Android 12 起支持真实背景模糊。", {})
                    GroupDivider()
                    SettingRow(Icons.Outlined.Fullscreen, "全面沉浸", "状态栏与底部手势区保持透明，交互控件避让系统手势。", {})
                } }
            }
            if (page == "阅读") {
                item { SectionLabel("正文") }
                item { GlassGroup(ambient) {
                    SettingRow(Icons.Outlined.TextFields, "阅读字号", "${state.fontSize.toInt()} sp", {})
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassButton("−", { model.fontSize((state.fontSize - 2).coerceAtLeast(14f)) }, backdrop = ambient, enabled = state.fontSize > 14)
                        Text("保持好奇，持续分享。", Modifier.weight(1f), fontSize = state.fontSize.sp, lineHeight = (state.fontSize * 1.7).sp)
                        GlassButton("+", { model.fontSize((state.fontSize + 2).coerceAtMost(26f)) }, backdrop = ambient, enabled = state.fontSize < 26)
                    }
                } }
                item { GlassGroup(ambient) { SettingRow(Icons.Outlined.Swipe, "原生阅读手势", "打开与关闭文章使用弹性过渡；返回手势支持跟手预览与取消回弹。", {}) } }
            }
            if (page == "通知") item { NotificationPreferences(ambient) }
            if (page == "检查更新") item { UpdatePreferences(ambient) }
            if (page == "账号") {
                item { GlassGroup(ambient) {
                    Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        NativeAvatar(account.avatar, account.displayName ?: "园", Modifier.size(76.dp))
                        Column { Text(account.displayName ?: "尚未授权", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(if (account.loggedIn) "博客园 API 身份已验证" else "请重新授权", fontSize = 12.sp) }
                    }
                    GroupDivider()
                    ProfileInformation(account)
                    GroupDivider()
                    SettingRow(Icons.Outlined.Refresh, "验证授权", account.message, model::refreshAccount)
                    GroupDivider()
                    SettingRow(Icons.Outlined.Logout, if (confirmLogout) "确认退出登录" else "退出登录", "清除本机授权令牌，保留收藏", {
                        if (confirmLogout) { model.logout(); onBack() } else confirmLogout = true
                    })
                } }
                if (!account.loggedIn) item { GlassButton("原生授权", onLogin, Modifier.fillMaxWidth(), ambient) }
            }
            if (page == "关于") {
                item { GlassGroup(ambient) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CnblogsMark(Modifier.size(64.dp))
                    Text("博客园 1.4.0", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Kotlin · Jetpack Compose · 全原生阅读", fontSize = 13.sp)
                    Text("博客园第三方客户端，与博客园官方无关联。图标取自博客园官网资源，图标与文章版权归其权利人所有。",
                        fontSize = 13.sp, lineHeight = 23.sp)
                    Text("搜索当前加载的网页文章，收藏保存在本机。授权使用官方 PAT，不收集账号密码。", fontSize = 13.sp, lineHeight = 23.sp)
                } } }
            }
        }
        EdgeVeil(content, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 112.dp))
        EdgeVeil(content, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom + 50.dp))
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回我的", onBack, backdrop = content)
        }
    }
}

@Composable private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f))
}
@Composable private fun GlassGroup(backdrop: GlassBackdrop, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), backdrop, RoundedCornerShape(32.dp)) { Column(Modifier.fillMaxWidth(), content = content) }
}
@Composable private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
}
@Composable private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, click: () -> Unit,
    trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 20.dp, vertical = 19.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(27.dp), MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun GlassToggle(checked: Boolean, toggle: () -> Unit) {
    GlassPanel(Modifier.width(58.dp).height(34.dp).semantics { role = Role.Switch; stateDescription = if (checked) "已开启" else "已关闭" },
        shape = CircleShape, selected = checked, onClick = toggle) {
        Box(Modifier.padding(4.dp).size(26.dp).align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
            .clip(CircleShape).background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
    }
}
