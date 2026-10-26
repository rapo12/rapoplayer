package com.rapoplayer.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** One layer, no outgoing screen retained, no layout animation on D-pad input.
 * Compose's animation clock applies the system animator-duration scale, including 0. */
fun Modifier.tvEnter(key: Any): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    val distance = with(LocalDensity.current) { 6.dp.toPx() }
    LaunchedEffect(key) { progress.animateTo(1f, tween(150, easing = FastOutSlowInEasing)) }
    graphicsLayer {
        alpha = .94f + .06f * progress.value
        translationY = distance * (1f - progress.value)
    }
}
