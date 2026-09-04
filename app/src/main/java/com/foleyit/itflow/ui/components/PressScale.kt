package com.foleyit.itflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.foleyit.itflow.ui.theme.Motion

/**
 * Springy press-down scale (design reference: buttons/cards -> 0.95x, FAB -> 0.90x) using a raw
 * pointer-event press/release detector rather than a clickable's own InteractionSource, so it
 * layers onto Button/Card/FloatingActionButton/etc. without needing each call site to plumb
 * through a custom interactionSource — `requireUnconsumed = false` and never calling `.consume()`
 * means the touch still passes through to whatever `.clickable`/`onClick` sits underneath this
 * modifier in the chain.
 */
fun Modifier.pressScale(scale: Float = 0.95f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = Motion.fast(),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}
