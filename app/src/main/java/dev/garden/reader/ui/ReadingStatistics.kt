package dev.garden.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garden.reader.features.*
import java.time.LocalDate

@Composable
internal fun ReadingStatistics(backdrop: GlassBackdrop, snapshot: ReadingSnapshot? = null) {
    val context = LocalContext.current
    val data = snapshot ?: remember { ReadingStats(context).snapshot() }
    val today = LocalDate.now(); val dates = remember(today) { ReadingMath.dates(today) }
    var selected by remember { mutableStateOf(today) }
    var range by remember { mutableIntStateOf(1) }
    val total = data.days.filterKeys { range == 3 || !it.isBefore(today.minusDays(listOf(7, 30, 90)[range] - 1L)) }.values.sum()
    fun duration(value: Long) = "${value / 3_600_000} 小时 ${(value / 60_000) % 60} 分钟"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        GlassPanel(Modifier.fillMaxWidth(), backdrop) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("阅读热力图", style = MaterialTheme.typography.titleLarge)
                val months = dates.filter { it.dayOfMonth == 1 }.map { "${it.monthValue}月" }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { months.forEach { Text(it, fontSize = 10.sp) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    dates.chunked(7).forEach { week -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            val millis = data.days[day] ?: 0
                            val color = lerp(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.primary,
                                ReadingMath.level(millis) / 4f)
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                                .background(if (day.isAfter(today)) color.copy(alpha = .2f) else color)
                                .semantics { contentDescription = "$day 阅读 ${millis / 1000} 秒"; this.selected = day == selected }
                                .clickable(enabled = !day.isAfter(today)) { selected = day })
                        }
                    } }
                }
                Text("$selected · ${duration(data.days[selected] ?: 0)}", fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("少  ", fontSize = 11.sp)
                    (0..4).forEach { level -> Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(RoundedCornerShape(3.dp))
                        .background(lerp(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.primary, level / 4f))) }
                    Text("  多", fontSize = 11.sp)
                }
            }
        }
        GlassSegments(listOf("7天", "30天", "90天", "全部"), range, { range = it }, backdrop, Modifier.fillMaxWidth())
        GlassPanel(Modifier.fillMaxWidth(), backdrop) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(duration(total), style = MaterialTheme.typography.headlineMedium); Text("所选时间阅读时长")
            Text("累计阅读 ${data.articles} 篇 · 阅读 ${data.days.count { it.value > 0 }} 天", style = MaterialTheme.typography.bodySmall)
            Text("今日 ${duration(data.days[today] ?: 0)} · 累计 ${duration(data.total)}", style = MaterialTheme.typography.bodySmall)
        } }
        Text("仅统计本机从新版开始的前台正文停留时间。后台、锁屏、加载中及覆盖页面不计入；不推算或补造历史数据。", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
