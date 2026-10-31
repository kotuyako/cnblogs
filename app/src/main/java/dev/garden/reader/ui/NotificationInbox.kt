package dev.garden.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.garden.reader.data.Article
import dev.garden.reader.features.ArticleNotifications
import dev.garden.reader.features.NotificationHistory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Embedded in the existing settings list; uses its parent's edge-to-edge/backdrop and scrolling. */
@Composable
internal fun NotificationInbox(backdrop: GlassBackdrop, onOpen: (Article) -> Unit) {
    val context = LocalContext.current
    val history = remember(context) { NotificationHistory.get(context) }
    val state by history.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var systemAllowed by remember { mutableStateOf(ArticleNotifications.allowed(context)) }
    var visibleCount by rememberSaveable { mutableIntStateOf(20) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) systemAllowed = ArticleNotifications.allowed(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassPanel(Modifier.fillMaxWidth(), backdrop) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("新文章记录", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${state.unreadCount} 条未读 · 共 ${state.records.size} 条", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when {
                    state.checking -> "正在检查博客园最新公开文章…"
                    state.lastCheckedAt > 0 -> "最近成功检查：${formatter.format(Date(state.lastCheckedAt))}"
                    state.enabled -> "等待首次基线检查；不会把历史文章当作新文章"
                    else -> "开启上方开关后，立即建立基线并定期检查新文章"
                }, fontSize = 13.sp, lineHeight = 22.sp)
                if (state.enabled && !systemAllowed) Text("系统通知不可用，但仍会保存站内新文章记录。请在系统设置允许通知。",
                    fontSize = 12.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.lastError?.let { Text(it, fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.error) }
                message?.let { Text(it, fontSize = 12.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.primary) }
                if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                GlassButton(if (state.checking) "正在检查…" else if (state.lastError != null) "重试检查" else "立即检查", {
                    scope.launch {
                        message = ArticleNotifications.checkNow(context).message
                        systemAllowed = ArticleNotifications.allowed(context)
                    }
                }, Modifier.fillMaxWidth(), backdrop, enabled = state.enabled && !state.checking)
                if (state.unreadCount > 0) GlassButton("全部标为已读", history::markAllRead, Modifier.fillMaxWidth(), backdrop)
            }
        }
        if (state.records.isEmpty()) GlassPanel(Modifier.fillMaxWidth(), backdrop) {
            Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(30.dp))
                Text(if (state.initialized) "基线之后还没有发现新文章" else "还没有新文章记录", fontWeight = FontWeight.SemiBold)
                Text("这里只显示实际检测到的新文章，不会生成测试通知或占位内容。", fontSize = 12.sp,
                    lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.records.take(visibleCount).forEach { notice -> key(notice.article.url) {
            GlassPanel(Modifier.fillMaxWidth(), backdrop, selected = !notice.read, onClick = {
                history.markRead(notice.article.url)
                onOpen(notice.article)
            }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(notice.article.title, fontWeight = if (notice.read) FontWeight.Medium else FontWeight.Bold,
                        fontSize = 17.sp, lineHeight = 26.sp)
                    Text("${if (notice.read) "已读" else "未读"} · ${notice.article.author} · ${formatter.format(Date(notice.discoveredAt))}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } }
        if (visibleCount < state.records.size) GlassButton("显示更多记录", { visibleCount += 20 }, Modifier.fillMaxWidth(), backdrop)
        Text("最近 200 条记录保存在本机。后台最短检查周期约 15 分钟，受系统省电与网络限制；不是服务器实时推送。",
            Modifier.padding(horizontal = 8.dp), fontSize = 11.sp, lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
