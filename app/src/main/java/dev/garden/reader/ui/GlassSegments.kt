package dev.garden.reader.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
internal fun GlassSegments(labels: List<String>, selected: Int, select: (Int) -> Unit,
    backdrop: GlassBackdrop, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val positions = remember(labels) { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val target = positions[selected] ?: (0f to 0f)
    val x by animateDpAsState(with(density) { target.first.toDp() }, spring(dampingRatio = .85f, stiffness = 500f), label = "segment x")
    val width by animateDpAsState(with(density) { target.second.toDp() }, spring(stiffness = 500f), label = "segment width")
    GlassPanel(modifier, backdrop, CircleShape) {
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp)) {
            Box(Modifier.offset(x = x).width(width).height(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)))
            Row {
                labels.forEachIndexed { index, label ->
                    Box(Modifier.onGloballyPositioned { positions[index] = it.positionInParent().x to it.size.width.toFloat() }
                        .height(44.dp).clip(CircleShape).semantics { role = Role.Tab; this.selected = index == selected }
                        .clickable { select(index) }.padding(horizontal = 18.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(label, color = if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
