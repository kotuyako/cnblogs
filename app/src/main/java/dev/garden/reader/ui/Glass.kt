package dev.garden.reader.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max

/**
 * A retained display list, not a screen bitmap. A source must be drawn BEFORE its consumers.
 * Never give a panel the same backdrop that records that panel: use one ambient source for
 * cards and a second source that records those cards for an overlaid navigation bar.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var position by mutableStateOf(Offset.Zero)
    internal var ready by mutableStateOf(false)
    internal var coordinates: LayoutCoordinates? = null
    internal var recording = false
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Records this subtree while also rendering it normally. Layers are released by composition. */
fun Modifier.recordGlassBackdrop(backdrop: GlassBackdrop): Modifier =
    onGloballyPositioned {
        backdrop.coordinates = it
        backdrop.position = it.positionInRoot()
    }
        .drawWithContent {
            // Guard against accidental nested use of the same source.
            if (backdrop.recording || size.width <= 0f || size.height <= 0f) {
                drawContent()
            } else {
                backdrop.recording = true
                try {
                    backdrop.layer.record {
                        this@drawWithContent.drawContent()
                    }
                } finally {
                    backdrop.recording = false
                }
                backdrop.ready = true
                drawLayer(backdrop.layer)
            }
        }

/**
 * Real backdrop sampling on Android 12+: only the small area behind this panel is blurred.
 * Padding around the sample avoids hard blur seams. Text/icons are drawn afterwards, sharp.
 * Older devices receive a more opaque glass tint, without an expensive software blur.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Allocate a second display list only when it can actually be used.
    val sampleLayer = if (supportsBlur && backdrop != null) rememberGraphicsLayer() else null
    val density = LocalDensity.current
    val radius = with(density) { 18.dp.toPx() }
    val blurEffect = remember(radius, supportsBlur) {
        if (supportsBlur) BlurEffect(radius, radius, TileMode.Clamp) else null
    }
    var position by remember { mutableStateOf(Offset.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 550f), label = "glass press",
    )
    val tintAlpha = when {
        !supportsBlur || backdrop == null -> if (dark) 0.86f else 0.90f
        selected -> if (dark) 0.50f else 0.48f
        else -> if (dark) 0.40f else 0.34f
    }
    val tint = if (selected) colors.primaryContainer else colors.surface
    val highlight = if (dark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.82f)
    val border = remember(colors, dark, selected) {
        Brush.linearGradient(
            listOf(
                highlight,
                colors.primary.copy(alpha = if (selected) 0.34f else 0.09f),
                Color.White.copy(alpha = if (dark) 0.06f else 0.30f),
                highlight.copy(alpha = if (dark) 0.20f else 0.66f),
            ),
        )
    }
    val interactionModifier = if (onClick != null) Modifier.clickable(
        interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick,
    ) else Modifier

    Box(
        modifier = modifier
            .onGloballyPositioned {
                coordinates = it
                position = it.positionInRoot()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (selected) 9.dp.toPx() else 5.dp.toPx()
                this.shape = shape
                ambientShadowColor = Color.Black.copy(alpha = if (dark) 0.24f else 0.07f)
                spotShadowColor = colors.primary.copy(alpha = if (dark) 0.12f else 0.09f)
            }
            .clip(shape)
            .drawWithContent {
                val sourceCoordinates = backdrop?.coordinates
                val targetCoordinates = coordinates
                if (sampleLayer != null && backdrop != null && backdrop.ready &&
                    !backdrop.recording && sourceCoordinates?.isAttached == true &&
                    targetCoordinates?.isAttached == true
                ) {
                    val margin = ceil(radius * 1.5f)
                    // Read the position snapshots so layout movement invalidates this draw.
                    // localPositionOf also cancels any shared predictive-back scale/translation;
                    // subtracting root positions alone produces a shifted sample while scaled.
                    backdrop.position
                    position
                    val offset = targetCoordinates.localPositionOf(sourceCoordinates, Offset.Zero)
                    val sampleSize = IntSize(
                        max(1, ceil(size.width + margin * 2).toInt()),
                        max(1, ceil(size.height + margin * 2).toInt()),
                    )
                    sampleLayer.renderEffect = blurEffect
                    sampleLayer.clip = true
                    sampleLayer.record(size = sampleSize) {
                        translate(left = offset.x + margin, top = offset.y + margin) {
                            drawLayer(backdrop.layer)
                        }
                    }
                    translate(left = -margin, top = -margin) { drawLayer(sampleLayer) }
                }
                drawRect(tint.copy(alpha = tintAlpha))
                // A directional sheen and broad soft reflection make the rim read as curved glass.
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (dark) 0.055f else 0.34f),
                            Color.Transparent,
                            colors.primary.copy(alpha = if (selected) 0.10f else 0.035f),
                        ), start = Offset.Zero, end = Offset(size.width, size.height),
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = if (dark) 0.045f else 0.24f), Color.Transparent),
                        center = Offset(size.width * 0.10f, -size.height * 0.18f),
                        radius = max(size.width * 0.85f, 1f),
                    ),
                )
                drawContent()
            }
            .border(1.dp, border, shape)
            .then(interactionModifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    selected: Boolean = false,
) {
    GlassPanel(
        modifier = modifier.size(48.dp), backdrop = backdrop, shape = CircleShape,
        onClick = onClick, selected = selected,
    ) {
        Icon(
            imageVector = icon, contentDescription = description,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).size(22.dp),
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    GlassPanel(
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { if (!enabled) disabled() },
        backdrop = backdrop, shape = RoundedCornerShape(20.dp),
        onClick = if (enabled) onClick else null, selected = selected,
    ) {
        Text(
            text, modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = (if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                .copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

/** Static Monet-colored light pools; no permanent animation or bitmap allocation. */
@Composable
fun LiquidBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    Canvas(modifier) {
        drawRect(colors.background)
        drawRect(Brush.radialGradient(listOf(colors.primary.copy(alpha = if (dark) .08f else .045f), Color.Transparent),
            center = Offset(size.width * .9f, size.height * .1f), radius = max(size.width, 1f)))
    }
}
