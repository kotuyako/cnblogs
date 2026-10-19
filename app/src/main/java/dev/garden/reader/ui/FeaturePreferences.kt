package dev.garden.reader.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garden.reader.account.AccountState
import dev.garden.reader.features.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ProfileInformation(account: AccountState) {
    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("基本信息", fontWeight = FontWeight.SemiBold)
        listOf("园号" to account.gardenNumber, "入园时间" to account.joined,
            "园豆" to account.beans, "会员" to account.membership, "园龄" to account.seniority).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value ?: "官方接口暂未提供", fontSize = 13.sp)
            }
        }
        Text("这里仅显示官方接口实际返回的数据，不推算入园日期、不将 API 授权误认为 VIP 会员。", fontSize = 11.sp,
            lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun NotificationPreferences(backdrop: GlassBackdrop) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ArticleNotifications.enabled(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    fun enable(value: Boolean) {
        runCatching { ArticleNotifications.setEnabled(context, value) }
            .onSuccess { enabled = ArticleNotifications.enabled(context); message = if (value) "已安排后台检查，首次运行只记录基线，不推送历史文章。" else "已关闭后台检查。" }
            .onFailure { message = it.message; enabled = ArticleNotifications.enabled(context) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) enable(true) else message = "通知权限未授予，未开启后台通知。可在系统设置中允许。"
    }
    GlassPanel(Modifier.fillMaxWidth(), backdrop) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("新文章通知", fontWeight = FontWeight.SemiBold)
                GlassToggle(enabled) {
                    when {
                        enabled -> enable(false)
                        Build.VERSION.SDK_INT >= 33 && !ArticleNotifications.allowed(context) -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        !ArticleNotifications.allowed(context) -> { message = "系统通知已关闭，请先允许通知。" }
                        else -> enable(true)
                    }
                }
            }
            Text("约每 15 分钟检查博客园首页。系统省电、后台限制和网络会延迟任务；这不是实时服务器推送。发现新文章后合并通知，点击可原生打开正文。", fontSize = 13.sp, lineHeight = 23.sp)
            message?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            GlassButton("系统通知设置", { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, backdrop = backdrop)
        }
    }
}

@Composable
internal fun UpdatePreferences(backdrop: GlassBackdrop) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("当前版本 ${ReleaseUpdates.VERSION}。检查 GitHub 正式发布版本。") }
    var url by remember { mutableStateOf<String?>(null) }
    GlassPanel(Modifier.fillMaxWidth(), backdrop) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(message, lineHeight = 23.sp)
            GlassButton(if (checking) "正在检查…" else "检查更新", {
                checking = true; url = null
                scope.launch {
                    try { val result = ReleaseUpdates.check(); message = result.message; url = result.url }
                    catch (c: CancellationException) { throw c }
                    catch (e: Exception) { message = e.message ?: "检查失败，请稍后重试" }
                    finally { checking = false }
                }
            }, Modifier.fillMaxWidth(), backdrop, enabled = !checking)
            url?.let { link -> GlassButton("查看发布版本", { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }, backdrop = backdrop) }
            Text("不会静默下载或安装 APK；更新需要你确认。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
