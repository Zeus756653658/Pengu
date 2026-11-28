package com.devrayan.pengu.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated Pengu mascot lifting his right flipper to wave "Hi!" during app startup.
 */
@Composable
fun PenguWavingMascot(
    badgeStyle: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onGreetingFinished: () -> Unit = {},
) {
    val bodyScale = remember { Animatable(0.70f) }
    val bodyHopY = remember { Animatable(10f) }
    val bodyTilt = remember { Animatable(0f) }
    val rightArmAngle = remember { Animatable(-8f) }
    val hiScale = remember { Animatable(0f) }
    var showHiBubble by remember { mutableStateOf(false) }
    var isBlinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 1. Snappy bounce entrance + lift right hand
        launch {
            bodyScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
        launch {
            bodyHopY.animateTo(-7f, tween(180, easing = FastOutSlowInEasing))
            bodyHopY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        launch {
            delay(90)
            rightArmAngle.animateTo(-124f, tween(210, easing = FastOutSlowInEasing))
            // Two cheerful waves
            rightArmAngle.animateTo(-150f, tween(130, easing = FastOutSlowInEasing))
            rightArmAngle.animateTo(-108f, tween(130, easing = FastOutSlowInEasing))
            rightArmAngle.animateTo(-146f, tween(130, easing = FastOutSlowInEasing))
            rightArmAngle.animateTo(-120f, tween(140, easing = FastOutSlowInEasing))
        }
        launch {
            delay(140)
            bodyTilt.animateTo(-5f, tween(180, easing = FastOutSlowInEasing))
            bodyTilt.animateTo(3f, tween(220, easing = FastOutSlowInEasing))
            bodyTilt.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
        }
        launch {
            delay(200)
            showHiBubble = true
            hiScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
        launch {
            delay(540)
            isBlinking = true
            delay(110)
            isBlinking = false
        }

        delay(1280)
        onGreetingFinished()
    }

    val cardBg =
        when (badgeStyle) {
            1 -> Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF)))
            else ->
                if (isDark) Brush.linearGradient(listOf(Color(0xFF23232E), Color(0xFF2B283A)))
                else Brush.linearGradient(listOf(Color.White, Color(0xFFF9F7FF)))
        }

    Box(
        modifier = modifier.size(width = 190.dp, height = 148.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Rounded Hero Emblem Card
        Box(
            modifier =
                Modifier.size(120.dp)
                    .graphicsLayer {
                        scaleX = bodyScale.value
                        scaleY = bodyScale.value
                        translationY = bodyHopY.value * density
                    }
                    .shadow(20.dp, RoundedCornerShape(32.dp))
                    .background(cardBg, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier =
                    Modifier.size(116.dp).graphicsLayer { rotationZ = bodyTilt.value }
            ) {
                val u = size.minDimension
                val cx = size.width * 0.49f
                val cy = size.height * 0.54f
                val ink = Color(0xFF14151A)
                val beakOrange = Color(0xFFFFA726)
                val strokeW = u * 0.052f

                // Left & right little feet
                drawOval(
                    color = beakOrange,
                    topLeft = Offset(cx - u * 0.22f, cy + u * 0.235f),
                    size = Size(u * 0.155f, u * 0.09f),
                )
                drawOval(
                    color = ink,
                    topLeft = Offset(cx - u * 0.22f, cy + u * 0.235f),
                    size = Size(u * 0.155f, u * 0.09f),
                    style = Stroke(width = strokeW * 0.88f, cap = StrokeCap.Round),
                )
                drawOval(
                    color = beakOrange,
                    topLeft = Offset(cx + u * 0.065f, cy + u * 0.235f),
                    size = Size(u * 0.155f, u * 0.09f),
                )
                drawOval(
                    color = ink,
                    topLeft = Offset(cx + u * 0.065f, cy + u * 0.235f),
                    size = Size(u * 0.155f, u * 0.09f),
                    style = Stroke(width = strokeW * 0.88f, cap = StrokeCap.Round),
                )

                // Left relaxed flipper
                val leftArmPath =
                    Path().apply {
                        moveTo(cx - u * 0.185f, cy - u * 0.03f)
                        cubicTo(
                            cx - u * 0.305f,
                            cy + u * 0.05f,
                            cx - u * 0.32f,
                            cy + u * 0.18f,
                            cx - u * 0.225f,
                            cy + u * 0.185f,
                        )
                        cubicTo(
                            cx - u * 0.18f,
                            cy + u * 0.18f,
                            cx - u * 0.18f,
                            cy + u * 0.08f,
                            cx - u * 0.185f,
                            cy - u * 0.03f,
                        )
                        close()
                    }
                drawPath(leftArmPath, color = Color.White)
                drawPath(
                    leftArmPath,
                    color = ink,
                    style = Stroke(width = strokeW * 0.92f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // Animated Right Flipper (rotates counter-clockwise up to wave!)
                val rightPivot = Offset(cx + u * 0.178f, cy - u * 0.035f)
                rotate(degrees = rightArmAngle.value, pivot = rightPivot) {
                    val armPath =
                        Path().apply {
                            moveTo(rightPivot.x - u * 0.025f, rightPivot.y)
                            cubicTo(
                                rightPivot.x + u * 0.075f,
                                rightPivot.y + u * 0.075f,
                                rightPivot.x + u * 0.085f,
                                rightPivot.y + u * 0.22f,
                                rightPivot.x - u * 0.005f,
                                rightPivot.y + u * 0.235f,
                            )
                            cubicTo(
                                rightPivot.x - u * 0.065f,
                                rightPivot.y + u * 0.23f,
                                rightPivot.x - u * 0.06f,
                                rightPivot.y + u * 0.09f,
                                rightPivot.x - u * 0.025f,
                                rightPivot.y,
                            )
                            close()
                        }
                    drawPath(armPath, color = Color.White)
                    drawPath(
                        armPath,
                        color = ink,
                        style = Stroke(width = strokeW * 0.92f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                // Penguin rounded head + chubby pear body
                val bodyPath =
                    Path().apply {
                        moveTo(cx, cy - u * 0.295f)
                        cubicTo(
                            cx + u * 0.185f,
                            cy - u * 0.295f,
                            cx + u * 0.225f,
                            cy - u * 0.145f,
                            cx + u * 0.195f,
                            cy - u * 0.025f,
                        )
                        cubicTo(
                            cx + u * 0.245f,
                            cy + u * 0.11f,
                            cx + u * 0.235f,
                            cy + u * 0.255f,
                            cx,
                            cy + u * 0.255f,
                        )
                        cubicTo(
                            cx - u * 0.235f,
                            cy + u * 0.255f,
                            cx - u * 0.245f,
                            cy + u * 0.11f,
                            cx - u * 0.195f,
                            cy - u * 0.025f,
                        )
                        cubicTo(
                            cx - u * 0.225f,
                            cy - u * 0.145f,
                            cx - u * 0.185f,
                            cy - u * 0.295f,
                            cx,
                            cy - u * 0.295f,
                        )
                        close()
                    }
                drawPath(bodyPath, color = Color.White)
                drawPath(
                    bodyPath,
                    color = ink,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // Soft happy cheek blushes
                drawOval(
                    color = Color(0xFFFF8A80).copy(alpha = 0.42f),
                    topLeft = Offset(cx - u * 0.155f, cy - u * 0.115f),
                    size = Size(u * 0.075f, u * 0.045f),
                )
                drawOval(
                    color = Color(0xFFFF8A80).copy(alpha = 0.42f),
                    topLeft = Offset(cx + u * 0.08f, cy - u * 0.115f),
                    size = Size(u * 0.075f, u * 0.045f),
                )

                // Cute expressive eyes (dot or happy curved blink)
                if (isBlinking) {
                    val leftBlink =
                        Path().apply {
                            moveTo(cx - u * 0.115f, cy - u * 0.145f)
                            quadraticTo(cx - u * 0.08f, cy - u * 0.175f, cx - u * 0.045f, cy - u * 0.145f)
                        }
                    val rightBlink =
                        Path().apply {
                            moveTo(cx + u * 0.045f, cy - u * 0.145f)
                            quadraticTo(cx + u * 0.08f, cy - u * 0.175f, cx + u * 0.115f, cy - u * 0.145f)
                        }
                    drawPath(leftBlink, color = ink, style = Stroke(width = strokeW * 0.65f, cap = StrokeCap.Round))
                    drawPath(rightBlink, color = ink, style = Stroke(width = strokeW * 0.65f, cap = StrokeCap.Round))
                } else {
                    drawCircle(color = ink, radius = u * 0.032f, center = Offset(cx - u * 0.08f, cy - u * 0.148f))
                    drawCircle(color = ink, radius = u * 0.032f, center = Offset(cx + u * 0.08f, cy - u * 0.148f))
                    // Tiny sparkle glint inside eyes
                    drawCircle(
                        color = Color.White,
                        radius = u * 0.010f,
                        center = Offset(cx - u * 0.088f, cy - u * 0.156f),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = u * 0.010f,
                        center = Offset(cx + u * 0.072f, cy - u * 0.156f),
                    )
                }

                // Friendly tilted mini eyebrows matching pengu_logo character
                drawLine(
                    color = ink,
                    start = Offset(cx - u * 0.105f, cy - u * 0.205f),
                    end = Offset(cx - u * 0.055f, cy - u * 0.195f),
                    strokeWidth = strokeW * 0.58f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = ink,
                    start = Offset(cx + u * 0.105f, cy - u * 0.205f),
                    end = Offset(cx + u * 0.055f, cy - u * 0.195f),
                    strokeWidth = strokeW * 0.58f,
                    cap = StrokeCap.Round,
                )

                // Cute rounded orange triangle beak
                val beakPath =
                    Path().apply {
                        moveTo(cx - u * 0.052f, cy - u * 0.11f)
                        lineTo(cx + u * 0.052f, cy - u * 0.11f)
                        lineTo(cx, cy - u * 0.05f)
                        close()
                    }
                drawPath(beakPath, color = beakOrange)
                drawPath(
                    beakPath,
                    color = ink,
                    style = Stroke(width = strokeW * 0.72f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        // Cute Floating "Hi!" Speech Bubble next to Pengu's raised waving hand
        AnimatedVisibility(
            visible = showHiBubble,
            enter = fadeIn(tween(150)) + scaleIn(tween(200)),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 4.dp),
        ) {
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = hiScale.value
                        scaleY = hiScale.value
                    }
                        .shadow(10.dp, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF))),
                            RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                        )
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Hi!",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}
