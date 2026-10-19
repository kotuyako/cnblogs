package dev.garden.reader.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The launcher, Android splash and Compose reveal all use the same official website mark. */
@Composable
fun GardenLaunch(onFinished: () -> Unit) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) {
        val motion = runCatching { Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        if (motion > 0f) {
            progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            delay(70)
            exit.animateTo(1f, tween(190))
        }
        onFinished()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - exit.value }
        .background(colors.background).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(colors.primary.copy(alpha = .12f * (1f - progress.value)),
                    radius = size.width * (.25f + .25f * progress.value))
            }
            CnblogsMark(Modifier.size(84.dp).graphicsLayer {
                scaleX = .72f + progress.value * .28f + exit.value * .05f
                scaleY = scaleX
                rotationZ = -8f * (1f - progress.value)
                alpha = .35f + .65f * progress.value
            })
        }
    }
}
