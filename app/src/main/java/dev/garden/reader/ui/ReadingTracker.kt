package dev.garden.reader.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.garden.reader.features.*
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
internal fun TrackReading(url: String, enabled: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = remember { ReadingStats(context) }
    val active by rememberUpdatedState(enabled)
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val pending = remember(url) { mutableMapOf<LocalDate, Long>() }
    fun flush() { pending.toMap().forEach { (day, millis) -> if (millis > 0) store.add(day, millis, url) }; pending.clear() }
    DisposableEffect(url, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) { resumed = false; flush() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); flush() }
    }
    LaunchedEffect(url, enabled, resumed) {
        if (!enabled || !resumed) return@LaunchedEffect
        var previous = SystemClock.elapsedRealtime()
        try {
            while (true) {
                delay(1000)
                val now = SystemClock.elapsedRealtime(); val millis = ReadingMath.delta(previous, now, active && resumed)
                previous = now
                if (millis > 0) { val day = LocalDate.now(); pending[day] = pending.getOrDefault(day, 0) + millis }
                if (pending.values.sum() >= 15000) flush()
            }
        } finally { flush() }
    }
}
