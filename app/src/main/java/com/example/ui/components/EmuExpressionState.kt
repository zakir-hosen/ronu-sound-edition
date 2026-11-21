package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue

/**
 * Continuous parametric eye expression model animated smoothly with bouncy spring physics.
 *
 * Sign convention for [browSlantDegrees]:
 * - Negative values (`-25f`) tilt the inner brow downward (focused / intense / skeptical).
 * - Positive values (`+25f`) tilt the outer brow downward (sad / worried / sleepy).
 * Left and right eyes automatically mirror [browSlantDegrees] symmetrically around the nose bridge.
 */
@Immutable
data class EyeExpressionParams(
    val topLidDrop: Float = 0.05f,
    val bottomLidRaise: Float = 0f,
    val browSlantDegrees: Float = 0f,
    val topArchCurve: Float = 0f,
    val bottomArchCurve: Float = 0f,
    val eyeScale: Float = 1f
)

/**
 * Built-in Emu facial expression presets selectable automatically via sensors/battery
 * or triggered directly from the Quick Expression Preview Chips.
 */
enum class EmuFacialExpression(val label: String) {
    NONE("None"),
    ALERT("Alert"),
    HAPPY("Happy"),
    CURIOUS("Curious"),
    SLEEPY("Sleepy"),
    SURPRISED("Surprised"),
    DIZZY("Dizzy"),
    WINK("Wink");

    companion object {
        fun fromName(name: String?): EmuFacialExpression {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ALERT
        }
    }
}

@Immutable
data class EmuEyePairExpression(
    val left: EyeExpressionParams,
    val right: EyeExpressionParams
)

object EmuExpressionPresets {

    val None = EmuEyePairExpression(
        left = EyeExpressionParams(
            topLidDrop = 0f,
            bottomLidRaise = 0f,
            browSlantDegrees = 0f,
            topArchCurve = 0f,
            bottomArchCurve = 0f,
            eyeScale = 1.0f
        ),
        right = EyeExpressionParams(
            topLidDrop = 0f,
            bottomLidRaise = 0f,
            browSlantDegrees = 0f,
            topArchCurve = 0f,
            bottomArchCurve = 0f,
            eyeScale = 1.0f
        )
    )

    val Alert = EmuEyePairExpression(
        left = EyeExpressionParams(
            topLidDrop = 0.05f,
            bottomLidRaise = 0f,
            browSlantDegrees = 0f,
            topArchCurve = 0f,
            bottomArchCurve = 0f,
            eyeScale = 1f
        ),
        right = EyeExpressionParams(
            topLidDrop = 0.05f,
            bottomLidRaise = 0f,
            browSlantDegrees = 0f,
            topArchCurve = 0f,
            bottomArchCurve = 0f,
            eyeScale = 1f
        )
    )

    val Happy = EmuEyePairExpression(
        left = EyeExpressionParams(
            topLidDrop = 0.06f,
            bottomLidRaise = 0.5f,
            browSlantDegrees = 0f,
            topArchCurve = 0.85f,
            bottomArchCurve = 0.7f,
            eyeScale = 1.04f
        ),
        right = EyeExpressionParams(
            topLidDrop = 0.06f,
            bottomLidRaise = 0.5f,
            browSlantDegrees = 0f,
            topArchCurve = 0.85f,
            bottomArchCurve = 0.7f,
            eyeScale = 1.04f
        )
    )

    val Curious = EmuEyePairExpression(
        left = EyeExpressionParams(
            topLidDrop = 0.02f,
            bottomLidRaise = 0f,
            browSlantDegrees = 8f,
            topArchCurve = 0.22f,
            bottomArchCurve = 0f,
            eyeScale = 1.08f
        ),
        right = EyeExpressionParams(
            topLidDrop = 0.32f,
            bottomLidRaise = 0.14f,
            browSlantDegrees = -14f,
            topArchCurve = -0.05f,
            bottomArchCurve = 0.14f,
            eyeScale = 0.96f
        )
    )

    val Sleepy = EmuEyePairExpression(
        left = EyeExpressionParams(
            topLidDrop = 0.48f,
            bottomLidRaise = 0.08f,
            browSlantDegrees = 12f,
            topArchCurve = -0.18f,
            bottomArchCurve = 0f,
            eyeScale = 0.96f
        ),
        right = EyeExpressionParams(
            topLidDrop = 0.48f,
            bottomLidRaise = 0.08f,
            browSlantDegrees = 12f,
            topArchCurve = -0.18f,
            bottomArchCurve = 0f,
            eyeScale = 0.96f
        )
    )

    val Surprised = EmuEyePairExpression(
        left = EyeExpressionParams(
            topLidDrop = 0f,
            bottomLidRaise = 0f,
            browSlantDegrees = 6f,
            topArchCurve = 0.22f,
            bottomArchCurve = 0f,
            eyeScale = 1.22f
        ),
        right = EyeExpressionParams(
            topLidDrop = 0f,
            bottomLidRaise = 0f,
            browSlantDegrees = 6f,
            topArchCurve = 0.22f,
            bottomArchCurve = 0f,
            eyeScale = 1.22f
        )
    )

    val Dizzy = EmuEyePairExpression(
        left = EyeExpressionParams(
            topLidDrop = 0.22f,
            bottomLidRaise = 0.18f,
            browSlantDegrees = -20f,
            topArchCurve = 0.25f,
            bottomArchCurve = -0.15f,
            eyeScale = 1.06f
        ),
        right = EyeExpressionParams(
            topLidDrop = 0.36f,
            bottomLidRaise = 0.10f,
            browSlantDegrees = 22f,
            topArchCurve = -0.25f,
            bottomArchCurve = 0.25f,
            eyeScale = 0.90f
        )
    )

    fun wink(leftTapped: Boolean): EmuEyePairExpression {
        val winkingEye = EyeExpressionParams(
            topLidDrop = 0.96f,
            bottomLidRaise = 0.45f,
            browSlantDegrees = -6f,
            topArchCurve = 0.40f,
            bottomArchCurve = 0.65f,
            eyeScale = 0.96f
        )
        val smilingCompanionEye = EyeExpressionParams(
            topLidDrop = 0.05f,
            bottomLidRaise = 0.28f,
            browSlantDegrees = 4f,
            topArchCurve = 0.55f,
            bottomArchCurve = 0.45f,
            eyeScale = 1.04f
        )
        return if (leftTapped) {
            EmuEyePairExpression(left = winkingEye, right = smilingCompanionEye)
        } else {
            EmuEyePairExpression(left = smilingCompanionEye, right = winkingEye)
        }
    }

    fun forExpression(
        expression: EmuFacialExpression,
        winkLeftEye: Boolean = true
    ): EmuEyePairExpression = when (expression) {
        EmuFacialExpression.NONE -> None
        EmuFacialExpression.ALERT -> Alert
        EmuFacialExpression.HAPPY -> Happy
        EmuFacialExpression.CURIOUS -> Curious
        EmuFacialExpression.SLEEPY -> Sleepy
        EmuFacialExpression.SURPRISED -> Surprised
        EmuFacialExpression.DIZZY -> Dizzy
        EmuFacialExpression.WINK -> wink(leftTapped = winkLeftEye)
    }
}

@Composable
fun rememberAnimatedEyeExpression(
    target: EyeExpressionParams,
    labelPrefix: String
): EyeExpressionParams {
    val expressionSpring = spring<Float>(dampingRatio = 0.65f, stiffness = 260f)

    val topLidDrop by animateFloatAsState(
        targetValue = target.topLidDrop.coerceIn(0f, 1f),
        animationSpec = expressionSpring,
        label = "${labelPrefix}_top_lid"
    )
    val bottomLidRaise by animateFloatAsState(
        targetValue = target.bottomLidRaise.coerceIn(0f, 1f),
        animationSpec = expressionSpring,
        label = "${labelPrefix}_bottom_lid"
    )
    val browSlantDegrees by animateFloatAsState(
        targetValue = target.browSlantDegrees.coerceIn(-25f, 25f),
        animationSpec = expressionSpring,
        label = "${labelPrefix}_brow_slant"
    )
    val topArchCurve by animateFloatAsState(
        targetValue = target.topArchCurve.coerceIn(-1f, 1f),
        animationSpec = expressionSpring,
        label = "${labelPrefix}_top_arch"
    )
    val bottomArchCurve by animateFloatAsState(
        targetValue = target.bottomArchCurve.coerceIn(-1f, 1f),
        animationSpec = expressionSpring,
        label = "${labelPrefix}_bottom_arch"
    )
    val eyeScale by animateFloatAsState(
        targetValue = target.eyeScale.coerceIn(0.85f, 1.25f),
        animationSpec = expressionSpring,
        label = "${labelPrefix}_eye_scale"
    )

    return EyeExpressionParams(
        topLidDrop = topLidDrop,
        bottomLidRaise = bottomLidRaise,
        browSlantDegrees = browSlantDegrees,
        topArchCurve = topArchCurve,
        bottomArchCurve = bottomArchCurve,
        eyeScale = eyeScale
    )
}
