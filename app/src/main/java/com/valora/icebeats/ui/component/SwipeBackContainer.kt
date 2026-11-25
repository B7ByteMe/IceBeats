package com.valora.icebeats.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.input.pointer.PointerEventPass

/**
 * A container providing an interactive, finger-attached physical swipe-to-go-back gesture.
 * When dragging from the left screen edge, the screen translates directly under the user's finger.
 * If released past threshold, it smoothly completes the exit and invokes [onBack].
 */
@Composable
fun SwipeBackContainer(
    enabled: Boolean,
    canSwipeBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    edgeWidthDp: Float = 72f,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val edgeWidthPx = with(density) { edgeWidthDp.dp.toPx() }

    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enabled && canSwipeBack) {
                    Modifier.pointerInput(canSwipeBack, screenWidthPx) {
                        awaitEachGesture {
                            // Intercept down at Initial pass so children cannot block edge detection
                            val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                            
                            // Only initiate swipe back if gesture starts within the left edge zone
                            if (down.position.x <= edgeWidthPx) {
                                var isDraggingBack = false
                                var totalDragX = 0f
                                var totalDragY = 0f

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) break

                                    val positionChange = change.positionChange()
                                    totalDragX += positionChange.x
                                    totalDragY += positionChange.y

                                    if (!isDraggingBack) {
                                        // Confirm horizontal dragging towards right
                                        if (totalDragX > 16f && abs(totalDragX) > abs(totalDragY) * 1.15f) {
                                            isDraggingBack = true
                                            change.consume()
                                        } else if (abs(totalDragY) > 24f && abs(totalDragY) > abs(totalDragX)) {
                                            // Vertical gesture dominant -> let child scrollables handle it
                                            break
                                        }
                                    }

                                    if (isDraggingBack) {
                                        change.consume()
                                        val newOffset = (offsetX.value + positionChange.x).coerceIn(0f, screenWidthPx)
                                        coroutineScope.launch {
                                            offsetX.snapTo(newOffset)
                                        }
                                    }
                                }

                                if (isDraggingBack) {
                                    val currentX = offsetX.value
                                    val threshold = screenWidthPx * 0.26f
                                    coroutineScope.launch {
                                        if (currentX >= threshold) {
                                            // Animate off-screen towards right and pop
                                            offsetX.animateTo(
                                                targetValue = screenWidthPx,
                                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                            )
                                            onBack()
                                            // Hold container off-screen until backstack transition settles to avoid visual flicker
                                            kotlinx.coroutines.delay(280)
                                            offsetX.snapTo(0f)
                                        } else {
                                            // Snap back to 0 with spring
                                            offsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        val currentOffset = offsetX.value

        // Dimming backdrop layer behind translating screen
        if (currentOffset > 0f) {
            val progress = (currentOffset / screenWidthPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - progress) * 0.40f
                    }
                    .background(Color.Black)
            )

            // Realistic 3D elevation shadow cast to the left of the sliding screen
            val shadowWidth = 24.dp
            val shadowWidthPx = with(density) { shadowWidth.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(shadowWidth)
                    .offset { IntOffset((currentOffset - shadowWidthPx).roundToInt().coerceAtLeast(0), 0) }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
        }

        // Attached Screen Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
        ) {
            content()
        }
    }
}
