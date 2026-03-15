package com.rekenrinkel.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Animation utilities that respect E-Ink mode
 * E-Ink screens should minimize animations to reduce ghosting
 */

/**
 * Crossfade that respects E-Ink mode
 * In E-Ink mode: instant switch without animation
 */
@Composable
fun <T> EInkCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    einkMode: Boolean = false,
    content: @Composable (T) -> Unit
) {
    if (einkMode) {
        // No animation in e-ink mode
        content(targetState)
    } else {
        Crossfade(
            targetState = targetState,
            modifier = modifier,
            animationSpec = tween(300)
        ) { state ->
            content(state)
        }
    }
}

/**
 * Animated visibility that respects E-Ink mode
 * In E-Ink mode: instant show/hide without animation
 */
@Composable
fun EInkAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    einkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    if (einkMode) {
        // No animation in e-ink mode
        if (visible) {
            content()
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(animationSpec = tween(150)) + expandVertically(),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically()
        ) {
            content()
        }
    }
}

/**
 * Content transform that respects E-Ink mode
 * In E-Ink mode: instant switch without animation
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EInkContentTransform(
    targetState: Boolean,
    modifier: Modifier = Modifier,
    einkMode: Boolean = false,
    content: @Composable () -> Unit,
    fallback: @Composable () -> Unit
) {
    if (einkMode) {
        // No animation in e-ink mode
        if (targetState) {
            content()
        } else {
            fallback()
        }
    } else {
        AnimatedContent(
            targetState = targetState,
            modifier = modifier,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)).togetherWith(
                    fadeOut(animationSpec = tween(150))
                )
            }
        ) { state ->
            if (state) {
                content()
            } else {
                fallback()
            }
        }
    }
}

/**
 * Returns animation duration (in ms) based on E-Ink mode
 * E-Ink: 0ms (no animation)
 * Normal: specified duration
 */
fun einkAnimationDuration(normalDuration: Int, einkMode: Boolean): Int {
    return if (einkMode) 0 else normalDuration
}

/**
 * Returns tween spec based on E-Ink mode
 * E-Ink: instant (0ms)
 * Normal: smooth animation
 */
fun <T> einkTween(einkMode: Boolean, normalDuration: Int = 300): TweenSpec<T> {
    return tween(
        durationMillis = if (einkMode) 0 else normalDuration,
        easing = FastOutSlowInEasing
    )
}