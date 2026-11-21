package com.example.sensor

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.GyroSensitivity
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Immutable
data class ParallaxState(
    val pitch: Float,
    val roll: Float,
    val multiplier: Float
) {
    val isActive: Boolean get() = multiplier > 0f

    // Layer 1: Background subtle slow drift (in dp)
    val bgTranslationXDp: Dp get() = (-roll * 5f * multiplier).dp
    val bgTranslationYDp: Dp get() = (pitch * 5f * multiplier).dp

    // Layer 2: Flip Clock & Date moderate 3D tilt + opposite drop shadow
    val clockRotationX: Float get() = pitch * 7.5f * multiplier
    val clockRotationY: Float get() = roll * 7.5f * multiplier
    val clockTranslationXDp: Dp get() = (roll * 9f * multiplier).dp
    val clockTranslationYDp: Dp get() = (-pitch * 9f * multiplier).dp
    val clockShadowOffsetXDp: Dp get() = (-roll * 8f * multiplier).dp
    val clockShadowOffsetYDp: Dp get() = (6f + pitch * 7f * multiplier).dp

    // Layer 3: Text App List closer Z-plane with weightless floating parallax
    val appsRotationX: Float get() = pitch * 5.5f * multiplier
    val appsRotationY: Float get() = roll * 6.5f * multiplier
    val appsTranslationXDp: Dp get() = (roll * 16f * multiplier).dp
    val appsTranslationYDp: Dp get() = (-pitch * 14f * multiplier).dp
}

@Composable
fun rememberParallaxState(
    tiltFlow: StateFlow<TiltVector>,
    sensitivity: GyroSensitivity
): ParallaxState {
    val rawTilt by tiltFlow.collectAsStateWithLifecycle()
    val targetPitch = if (sensitivity == GyroSensitivity.OFF) 0f else rawTilt.pitch
    val targetRoll = if (sensitivity == GyroSensitivity.OFF) 0f else rawTilt.roll

    val smoothPitch by animateFloatAsState(
        targetValue = targetPitch,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "parallax_pitch"
    )

    val smoothRoll by animateFloatAsState(
        targetValue = targetRoll,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "parallax_roll"
    )

    val smoothMultiplier by animateFloatAsState(
        targetValue = sensitivity.multiplier,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "parallax_multiplier"
    )

    return ParallaxState(
        pitch = smoothPitch,
        roll = smoothRoll,
        multiplier = smoothMultiplier
    )
}

@Composable
fun Modifier.parallaxLayer1Background(state: ParallaxState): Modifier {
    val density = LocalDensity.current
    return this.graphicsLayer {
        cameraDistance = 14f * density.density
        translationX = with(density) { state.bgTranslationXDp.toPx() }
        translationY = with(density) { state.bgTranslationYDp.toPx() }
    }
}

@Composable
fun Modifier.parallaxLayer2Clock(state: ParallaxState): Modifier {
    val density = LocalDensity.current
    return this.graphicsLayer {
        cameraDistance = 14f * density.density
        rotationX = state.clockRotationX
        rotationY = state.clockRotationY
        translationX = with(density) { state.clockTranslationXDp.toPx() }
        translationY = with(density) { state.clockTranslationYDp.toPx() }
    }
}

@Composable
fun Modifier.parallaxLayer3Apps(state: ParallaxState): Modifier {
    val density = LocalDensity.current
    return this.graphicsLayer {
        cameraDistance = 14f * density.density
        rotationX = state.appsRotationX
        rotationY = state.appsRotationY
        translationX = with(density) { state.appsTranslationXDp.toPx() }
        translationY = with(density) { state.appsTranslationYDp.toPx() }
    }
}
