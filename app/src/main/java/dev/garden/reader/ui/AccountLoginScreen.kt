package dev.garden.reader.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.garden.reader.account.GardenAccountSession
import kotlinx.coroutines.launch

@Composable
fun AccountLoginScreen(session: GardenAccountSession, onBack: () -> Unit) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        val wasSecure = activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val account by session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().recordGlassBackdrop(backdrop))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack, backdrop = backdrop)
            Spacer(Modifier.height(12.dp))
            GlassPanel(Modifier.size(72.dp), backdrop = backdrop, selected = true) {
                CnblogsMark(Modifier.align(Alignment.Center).size(46.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("欢迎回到园子", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("原生授权 · 安全连接博客园", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassInput(token, { if (!account.checking) token = it.trim() }, "个人访问令牌 PAT", Modifier.weight(1f), password = !visible, secureInput = true)
                GlassIconButton(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    "切换令牌可见性", { visible = !visible }, backdrop = backdrop)
            }
            GlassButton(if (account.checking) "正在验证身份…" else "授权并登录", {
                val submitted = token
                scope.launch { session.login(submitted); if (session.state.value.loggedIn) token = "" }
            }, Modifier.fillMaxWidth(), backdrop, selected = true, enabled = !account.checking && token.isNotBlank())
            account.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider()
            Text("如何获取授权？", fontWeight = FontWeight.SemiBold)
            Text("在博客园账号中心的「个人访问令牌」中创建令牌，然后粘贴到上方。\n\naccount.cnblogs.com/tokens",
                lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("这里不是网页，也不会收集你的账号密码。此版本使用官方支持的 PAT 授权。令牌仅发送给博客园 API，并由 Android Keystore 加密保存在本机。",
                style = MaterialTheme.typography.bodySmall, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("授权只验证 API 身份，不代表网页会员内容或评论功能已解锁。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
