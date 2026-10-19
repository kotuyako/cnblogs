package dev.garden.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garden.reader.data.NativeDocument

/** Studio renders these with Android Compose, not HTML or a web mockup. */
@Preview(name = "Native glass / light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun NavigationPreview() {
    GardenTheme("light", false) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().recordGlassBackdrop(backdrop).background(MaterialTheme.colorScheme.background).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Text("博客园", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("发现 · 技术与灵感", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                repeat(5) {
                    Text("用 Kotlin 与 Jetpack Compose，\n构建更轻盈的原生阅读体验", fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text("博客园  ·  10-18", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    HorizontalDivider()
                }
            }
            EdgeVeil(backdrop, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp))
            FloatingNavigation(0, {}, backdrop, Modifier.align(Alignment.BottomCenter).padding(18.dp))
        }
    }
}

@Preview(name = "Native article typography", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ReadingPreview() {
    GardenTheme("light", false) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(vertical = 38.dp)) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                Text("在代码之间，发现新的灵感", Modifier.padding(horizontal = 18.dp), fontSize = 27.sp,
                    lineHeight = 39.sp, fontWeight = FontWeight.Bold)
            }
            Text("2026-10-18  ·  博客园", Modifier.padding(22.dp), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            NativeDocument.parse("<p>阅读应该回到内容本身。清晰的文字、舒展的留白，以及恰到好处的层次，让每一次分享都值得认真阅读。</p><h2>纯原生，专注内容</h2><p>正文由原生组件排版，保留<strong>重要的强调</strong>，也让图片和代码回到合适的位置。</p><pre>val curiosity = remember { true }</pre>")
                .forEach { NativeArticleBlock(it, 18f, Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) }
        }
    }
}
