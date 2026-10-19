package dev.garden.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassSheet(title: String, backdrop: GlassBackdrop, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = dismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .14f)).clickable(onClick = dismiss)
        .statusBarsPadding().navigationBarsPadding().imePadding().padding(14.dp), contentAlignment = Alignment.BottomCenter) {
        GlassPanel(Modifier.widthIn(max = 620.dp).fillMaxWidth(), backdrop, RoundedCornerShape(32.dp), onClick = {}) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                content()
            }
        }
    }
}
