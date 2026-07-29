package com.rekluzlabs.vaultcuisine.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TimerCircle(
    activeTimer: ActiveTimer,
    tick: Long,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    strokeWidth: Dp = 5.dp,
    bgColor: Color = Color.White.copy(alpha = 0.15f),
    arcColor: Color = Color(0xFFFF9800),
    textColor: Color = Color.White,
    circleBgColor: Color = Color(0x1AFFFFFF)
) {
    val total = activeTimer.totalSeconds
    val remaining = activeTimer.remainingSeconds
    val progress = if (total > 0) remaining.toFloat() / total else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "timerProgress"
    )

    val minutes = remaining / 60
    val seconds = remaining % 60
    val digitMin = minutes.toString().padStart(2, '0')
    val digitSec = seconds.toString().padStart(2, '0')

    @Suppress("UNUSED_EXPRESSION")
    tick

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val arcStroke = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )

            val bgRadius = (size.toPx() - strokeWidth.toPx()) / 2f
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)

            drawCircle(
                color = circleBgColor,
                radius = bgRadius + 4.dp.toPx(),
                center = center
            )

            drawCircle(
                color = bgColor,
                radius = bgRadius,
                center = center,
                style = arcStroke
            )

            val sweep = animatedProgress * 360f

            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(
                    (size.toPx() - bgRadius * 2) / 2f,
                    (size.toPx() - bgRadius * 2) / 2f
                ),
                size = androidx.compose.ui.geometry.Size(bgRadius * 2, bgRadius * 2),
                style = arcStroke
            )
        }

        Text(
            text = "$digitMin:$digitSec",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.3f).value.sp,
                lineHeight = (size * 0.35f).value.sp
            ),
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}
