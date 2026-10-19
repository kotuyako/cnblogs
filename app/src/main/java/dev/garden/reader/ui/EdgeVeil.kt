package dev.garden.reader.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** A feathered real backdrop blur, not a blurred foreground text layer. */
@Composable
fun EdgeVeil(backdrop: GlassBackdrop, top: Boolean, modifier: Modifier = Modifier) {
    val sample = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val surface = MaterialTheme.colorScheme.background
    Box(modifier.onGloballyPositioned { coordinates = it }.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        val target = coordinates; val source = backdrop.coordinates
        if (Build.VERSION.SDK_INT >= 31 && backdrop.ready && !backdrop.recording &&
            target?.isAttached == true && source?.isAttached == true) {
            val offset = target.localPositionOf(source, Offset.Zero)
            val margin = 32.dp.toPx()
            sample.renderEffect = BlurEffect(16.dp.toPx(), 16.dp.toPx(), TileMode.Clamp)
            sample.record(size = IntSize((size.width + margin * 2).toInt(), (size.height + margin * 2).toInt())) {
                translate(offset.x + margin, offset.y + margin) { drawLayer(backdrop.layer) }
            }
            translate(-margin, -margin) { drawLayer(sample) }
        }
        drawRect(surface.copy(alpha = 0.76f))
        // Smoothstep feather: zero slope at both ends, no abrupt linear three-stop band.
        val stops = Array(33) { index ->
            val x = index / 32f
            val t = if (top) 1f - x else x
            val a = t * t * (3f - 2f * t)
            x to Color.White.copy(alpha = a)
        }
        drawRect(Brush.verticalGradient(*stops), blendMode = BlendMode.DstIn)
    })
}
