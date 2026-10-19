package dev.garden.reader.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One owner at a time: gesture, cancellation settle, or committed button/system back. */
private class BackTransition {
    var generation = 0
    var closing by mutableStateOf(false)
    var animation: Job? = null

    fun begin(): Int {
        generation += 1
        animation?.cancel()
        animation = null
        return generation
    }
}

/** Keep the previous destination composed behind this layer for a real back preview. */
@Composable
fun PredictiveGlassLayer(
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.(back: () -> Unit) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val entrance = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val transition = remember { BackTransition() }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val active by rememberUpdatedState(enabled)
    val commit: (Int) -> Unit = { generation ->
        transition.closing = true
        transition.animation = scope.launch {
            entrance.snapTo(1f)
            progress.animateTo(1f, tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            if (transition.generation == generation && active) {
                dismiss()
            }
        }
    }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = .9f, stiffness = 450f))
    }
    val back: () -> Unit = {
        if (active && !transition.closing) {
            edge = BackEventCompat.EDGE_LEFT
            commit(transition.begin())
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            // An overlaid destination owns back now. Restore this retained previous page.
            transition.begin()
            transition.closing = false
            progress.snapTo(0f)
        }
    }
    PredictiveBackHandler(enabled = enabled) { events ->
        // Keep consuming back during the short exit animation. Disabling the callback here
        // lets a second quick back escape to the previous page or finish the Activity.
        if (transition.closing || !active) {
            events.collect { }
            return@PredictiveBackHandler
        }
        val generation = transition.begin()
        try {
            entrance.snapTo(1f)
            events.collect { event ->
                // A button press or a newer gesture must never be undone by this old flow.
                if (transition.generation != generation) throw CancellationException()
                edge = event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            if (transition.generation == generation && active) commit(generation)
        } catch (cancelled: CancellationException) {
            if (transition.generation == generation && !transition.closing) {
                transition.animation = scope.launch {
                    progress.animateTo(0f, spring(stiffness = 650f))
                }
            }
            throw cancelled
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Stay outside the transformed page so even the revealed edge gaps consume touches.
        Box(Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .clearAndSetSemantics { })
        Box(Modifier.fillMaxSize().graphicsLayer {
            val fraction = progress.value.coerceIn(0f, 1f)
            val arriving = 1f - entrance.value.coerceIn(0f, 1f)
            scaleX = 1f - fraction * .02f
            scaleY = scaleX
            translationX = size.width * (arriving + fraction * if (edge == BackEventCompat.EDGE_LEFT) 1f else -1f)
            shape = RoundedCornerShape((24f * maxOf(fraction, arriving)).dp)
            clip = fraction > 0f || arriving > 0f
            shadowElevation = 18.dp.toPx() * maxOf(fraction, arriving)
        }.background(MaterialTheme.colorScheme.background)) {
            content(back)
        }
    }
}
