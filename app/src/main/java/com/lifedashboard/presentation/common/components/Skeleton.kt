package com.lifedashboard.presentation.common.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifedashboard.presentation.theme.Line
import com.lifedashboard.presentation.theme.Panel
import com.lifedashboard.presentation.theme.PanelHigh

/** Brillo que recorre un bloque mientras carga. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -400f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    background(Brush.linearGradient(listOf(Panel, PanelHigh, Panel), start = Offset(x, 0f), end = Offset(x + 400f, 300f)))
}

/** Bloque de carga con la forma del contenido que va a aparecer. */
@Composable
fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier, radius: Dp = 22.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(radius)).shimmer())
}
