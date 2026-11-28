package com.devrayan.pengu.ui.page.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Material You Expressive rotating scalloped play-button shape for Pengu Music Player.
 */
data class PenguWavyShape(
    val sides: Int,
    val indent: Float,
    val rotationDegrees: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val maxRadiusX = size.width / 2f
        val maxRadiusY = size.height / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        val steps = 120
        val rotationRad = rotationDegrees * PI / 180.0
        for (i in 0..steps) {
            val angle = i * PI * 2.0 / steps
            val bumpAngle = angle - rotationRad
            val r = 1f - indent + indent * cos(sides * bumpAngle)
            val x = cx + maxRadiusX * r * cos(angle)
            val y = cy + maxRadiusY * r * sin(angle)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat())
            else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Translucent pill seekbar that expands smoothly while dragging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenguAppleSlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val trackHeight by
        animateDpAsState(
            targetValue = if (isDragged || isPressed) 14.dp else 9.dp,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "penguSlimSliderHeight",
        )

    Slider(
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        colors =
            SliderDefaults.colors(
                activeTrackColor = activeColor,
                inactiveTrackColor = inactiveColor,
                thumbColor = activeColor,
            ),
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        track = { sliderState ->
            PenguSlimTrack(
                sliderState = sliderState,
                trackHeight = trackHeight,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PenguSlimTrack(
    sliderState: SliderState,
    trackHeight: Dp,
    activeColor: Color,
    inactiveColor: Color,
) {
    val range = sliderState.valueRange
    val span = (range.endInclusive - range.start).coerceAtLeast(1f)
    val fraction = ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(trackHeight)) {
        drawRoundedBar(fraction, inactiveColor, activeColor, trackHeight)
    }
}

private fun DrawScope.drawRoundedBar(
    fraction: Float,
    inactiveColor: Color,
    activeColor: Color,
    trackHeight: Dp,
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val left = Offset(0f, center.y)
    val right = Offset(size.width, center.y)
    val start = if (isRtl) right else left
    val end = if (isRtl) left else right
    val strokePx = trackHeight.toPx()
    drawLine(inactiveColor, start, end, strokePx, StrokeCap.Round)
    val activeEnd = Offset(start.x + (end.x - start.x) * fraction, center.y)
    drawLine(activeColor, start, activeEnd, strokePx, StrokeCap.Round)
}

/**
 * Animated Material You Squiggly Wave seekbar for Pengu Music Player.
 */
@Composable
fun PenguSquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
) {
    val primaryColor = colors.activeTrackColor
    val inactiveColor = colors.inactiveTrackColor

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(value) }

    val currentValue = if (isDragging) dragPosition else value
    val duration = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val fraction = ((currentValue - valueRange.start) / duration).coerceIn(0f, 1f)

    var phaseOffset by remember { mutableFloatStateOf(0f) }
    var heightFraction by remember { mutableFloatStateOf(if (isPlaying) 1f else 0f) }

    val waveLength = 72f
    val amplitude = 7f
    val phaseSpeed = 28f

    LaunchedEffect(isPlaying, isDragging) {
        val flatten = !isPlaying || isDragging
        val target = if (flatten) 0f else 1f
        if (!flatten) delay(30L)
        Animatable(heightFraction).animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = if (flatten) 150 else 200, easing = LinearEasing),
        ) {
            heightFraction = this.value
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                val dt = (frameTimeMillis - lastFrameTime) / 1000f
                phaseOffset = (phaseOffset + dt * phaseSpeed) % waveLength
                lastFrameTime = frameTimeMillis
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(valueRange) {
                            detectTapGestures { offset ->
                                val pos = (offset.x / size.width).coerceIn(0f, 1f) * duration
                                onValueChange(valueRange.start + pos)
                                onValueChangeFinished?.invoke()
                            }
                        }.pointerInput(valueRange) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val pos = (offset.x / size.width).coerceIn(0f, 1f) * duration
                                    dragPosition = valueRange.start + pos
                                    onValueChange(dragPosition)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    onValueChangeFinished?.invoke()
                                },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pos = (change.position.x / size.width).coerceIn(0f, 1f) * duration
                                    dragPosition = valueRange.start + pos
                                    onValueChange(dragPosition)
                                },
                            )
                        }
                    } else Modifier
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            val cy = size.height / 2f
            val totalW = size.width
            val activeW = totalW * fraction
            val strokePx = 4.dp.toPx()

            drawLine(
                color = inactiveColor,
                start = Offset(activeW, cy),
                end = Offset(totalW, cy),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )

            clipRect(left = 0f, top = 0f, right = activeW, bottom = size.height) {
                val path = Path()
                val step = 4f
                var x = 0f
                while (x <= totalW + step) {
                    val rad = ((x + phaseOffset) / waveLength) * (2f * PI.toFloat())
                    val taper = if (x > activeW - waveLength) ((activeW - x) / waveLength).coerceIn(0f, 1f) else 1f
                    val y = cy + sin(rad) * amplitude * heightFraction * taper
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += step
                }
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }

            val thumbHeight = 18.dp.toPx()
            val thumbWidth = 5.dp.toPx()
            drawLine(
                color = primaryColor,
                start = Offset(activeW, cy - thumbHeight / 2f),
                end = Offset(activeW, cy + thumbHeight / 2f),
                strokeWidth = thumbWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
