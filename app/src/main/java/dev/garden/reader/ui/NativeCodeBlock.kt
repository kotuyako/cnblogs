package dev.garden.reader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garden.reader.data.NativeBlock
import dev.garden.reader.features.CodeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NativeCodeBlock(block: NativeBlock, fontSize: Float, modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val context = LocalContext.current; val clipboard = LocalClipboardManager.current; val scope = rememberCoroutineScope()
    val code = block.text
    val preview = remember(code) { code.lineSequence().take(500).joinToString("\n").take(40000) }
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            message = try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(code.toByteArray(Charsets.UTF_8)) }
                ?: error("无法写入所选文件") }; "代码已保存" } catch (_: Exception) { "保存失败，请重试" }
        }
    }
    val annotated by produceState(AnnotatedString(preview), preview, dark) {
        value = withContext(Dispatchers.Default) {
            val builder = AnnotatedString.Builder(preview)
            CodeTokens.parse(preview).forEach { token ->
                val color = when (token.kind) {
                    "keyword" -> if (dark) Color(0xFFC792EA) else Color(0xFF7C3CAD)
                    "string" -> if (dark) Color(0xFFA8CC86) else Color(0xFF34772D)
                    "number" -> if (dark) Color(0xFFF1BA7B) else Color(0xFFA75B16)
                    "type" -> if (dark) Color(0xFF7AC7F4) else Color(0xFF176BA2)
                    else -> if (dark) Color(0xFF7F8B96) else Color(0xFF76818A)
                }
                builder.addStyle(SpanStyle(color = color, fontStyle = if (token.kind == "comment") FontStyle.Italic else null), token.start, token.end)
            }
            builder.toAnnotatedString()
        }
    }
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.clip(shape).background(if (dark) Color(0xFF141A1E) else Color(0xFFF6F8FA))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), shape)) {
        Row(Modifier.fillMaxWidth().background(if (dark) Color(0xFF20282D) else Color(0xFFE9EEF2)).padding(start = 16.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(block.language.ifBlank { "code" }, Modifier.weight(1f), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GlassIconButton(Icons.Outlined.Download, "保存完整代码", { export.launch("snippet." + when (block.language.lowercase()) {
                "kotlin", "kt" -> "kt"; "java" -> "java"; "json" -> "json"; "python", "py" -> "py"; "javascript", "js" -> "js"; else -> "txt" }) })
            GlassIconButton(Icons.Outlined.ContentCopy, "复制完整代码", { clipboard.setText(AnnotatedString(code)); message = "已复制完整代码" })
        }
        SelectionContainer {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val size = (fontSize - 3).coerceAtLeast(12f)
                Text((1..preview.lines().size).joinToString("\n"), fontSize = size.sp, lineHeight = (size * 1.65f).sp,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f))
                Text(annotated, fontSize = size.sp, lineHeight = (size * 1.65f).sp, fontFamily = FontFamily.Monospace,
                    color = if (dark) Color(0xFFD6DDE4) else Color(0xFF202830))
            }
        }
        if (preview.length < code.length) Text("长代码仅预览前 500 行 / 40000 字；复制或保存可获取完整内容。", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
    }
}
