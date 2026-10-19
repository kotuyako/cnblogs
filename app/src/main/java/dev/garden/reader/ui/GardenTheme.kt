package dev.garden.reader.ui

import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val GardenLight = lightColorScheme(
    primary = Color(0xFF228DA3), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F2F8), onPrimaryContainer = Color(0xFF073D48),
    secondary = Color(0xFF53685F), secondaryContainer = Color(0xFFD5E8DC),
    tertiary = Color(0xFF4E647F), tertiaryContainer = Color(0xFFD5E3FF),
    background = Color(0xFFF4F3F8), onBackground = Color(0xFF16171D),
    surface = Color(0xFFF9F9FC), onSurface = Color(0xFF16171D),
    surfaceContainer = Color(0xFFEBEDF3), surfaceContainerLow = Color(0xFFF0F1F6),
    surfaceContainerHigh = Color(0xFFE4E7EF), onSurfaceVariant = Color(0xFF646974),
    outline = Color(0xFF75857B), outlineVariant = Color(0xFFBCCBC0),
)

private val GardenDark = darkColorScheme(
    primary = Color(0xFF78D5E4), onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF194C58), onPrimaryContainer = Color(0xFFCCF2F8),
    secondary = Color(0xFFB8CDC1), secondaryContainer = Color(0xFF3B5145),
    tertiary = Color(0xFFB4C9E9), tertiaryContainer = Color(0xFF364C65),
    background = Color(0xFF111319), onBackground = Color(0xFFE9EDF4),
    surface = Color(0xFF1B1E27), onSurface = Color(0xFFE9EDF4),
    surfaceContainer = Color(0xFF242832), surfaceContainerLow = Color(0xFF191D25),
    surfaceContainerHigh = Color(0xFF303642), onSurfaceVariant = Color(0xFFADB5C3),
    outline = Color(0xFF849B8C), outlineVariant = Color(0xFF394D40),
)

private val GardenTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 23.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Monet uses the system wallpaper palette on Android 12+, with a complete older-device palette. */
@Composable
fun GardenTheme(theme: String, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = LocalInspectionMode.current
    var paletteRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) paletteRevision++
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { paletteRevision++ }
        }
        if (!preview) lifecycle.addObserver(observer)
        val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        if (!preview) {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(receiver, filter)
        }
        onDispose { if (!preview) { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) } }
    }
    val colors = remember(context, configuration, paletteRevision, dark, dynamicColor) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) GardenDark else GardenLight
    }
    MaterialTheme(colorScheme = colors, typography = GardenTypography) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides colors.onBackground,
        ) { content() }
    }
}
