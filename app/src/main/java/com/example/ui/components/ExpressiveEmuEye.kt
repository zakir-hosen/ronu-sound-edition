package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.example.ui.theme.FlipCardInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val GlintCyan = Color(0xFF22D3EE)
private val GlintEmerald = Color(0xFF34D399)
private val GlintMagenta = Color(0xFFF472B6)

/**
 * Custom Canvas Facial Expression Renderer for an Emu Eye containing `HH` or `MM` numerals.
 *
 * Uses [Modifier.drawWithContent] to render Quadratic Bezier upper & lower eyelid masks
 * rotated by `browSlantDegrees` directly over the eye base and inner numerals so the numbers
 * appear naturally tucked behind the eyelids, while applying inertial lag (`stiffness = 180f`)
 * to the numerals during 3D gyroscope tilts.
 */
@Composable
fun ExpressiveEmuEye(
    numerals: String,
    eyeColor: Color,
    isLeftEye: Boolean,
    expression: EyeExpressionParams,
    blinkScaleX: Float,
    blinkScaleY: Float,
    pupilTargetOffsetXPx: Float,
    pupilTargetOffsetYPx: Float,
    isDizzy: Boolean,
    dizzyPhase: Float,
    isCharging: Boolean,
    chargingPulseAlpha: Float,
    glitchIntensity: Float,
    contentDescriptionText: String,
    eyeTestTag: String,
    numeralTestTag: String,
    onTapWink: () -> Unit,
    modifier: Modifier = Modifier,
    eyeSize: Dp = 90.dp,
    maskColor: Color = MaterialTheme.colorScheme.background,
    currentEmotion: EmuFacialExpression = EmuFacialExpression.ALERT,
    isBlinking: Boolean = false
) {
    // Auto-adjust numeral contrast using luminance threshold
    val luminance = ColorUtils.calculateLuminance(eyeColor.toArgb())
    val numeralColor = if (luminance > 0.179) FlipCardInk else PureWhite

    // Inertial lag (stiffness = 180f) so HH/MM numerals trail slightly behind outer eye tilt
    val inertialLagSpec = remember {
        spring<Float>(dampingRatio = 0.65f, stiffness = 180f)
    }
    val laggedPupilXPx by animateFloatAsState(
        targetValue = pupilTargetOffsetXPx,
        animationSpec = inertialLagSpec,
        label = "${eyeTestTag}_lag_x"
    )
    val laggedPupilYPx by animateFloatAsState(
        targetValue = pupilTargetOffsetYPx,
        animationSpec = inertialLagSpec,
        label = "${eyeTestTag}_lag_y"
    )

    // Subtle corner-rounding dilation when eyeScale widens (> 1.08f)
    val surpriseFactor = ((expression.eyeScale - 1f) / 0.22f).coerceIn(0f, 1f)
    val eyeShape = RoundedCornerShape((26f + 16f * surpriseFactor).dp)

    // Mirror brow slant symmetrically around the nose bridge
    val signedBrowRotation = if (isLeftEye) -expression.browSlantDegrees else expression.browSlantDegrees

    Box(
        modifier = modifier
            .size(eyeSize)
            .graphicsLayer {
                scaleX = (blinkScaleX * expression.eyeScale).coerceIn(0.25f, 1.40f)
                scaleY = (blinkScaleY * expression.eyeScale).coerceIn(0.05f, 1.40f)
            }
            .clip(eyeShape)
            .background(eyeColor)
            .then(
                if (isCharging) {
                    Modifier.border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                GlintEmerald.copy(alpha = chargingPulseAlpha),
                                GlintCyan.copy(alpha = chargingPulseAlpha)
                            )
                        ),
                        shape = eyeShape
                    )
                } else if (glitchIntensity > 0.05f) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = GlintCyan.copy(alpha = 0.8f * glitchIntensity),
                        shape = eyeShape
                    )
                } else {
                    Modifier
                }
            )
            .drawBehind {
                if (isDizzy) {
                    val maxRadius = size.width * 0.42f
                    val spiralPath = Path()
                    val steps = 42
                    val turns = 2.2f
                    val center = Offset(size.width * 0.5f, size.height * 0.5f)
                    for (i in 0..steps) {
                        val t = i / steps.toFloat()
                        val angle = dizzyPhase + (t * turns * 2f * PI.toFloat()) *
                            if (isLeftEye) 1f else -1f
                        val radius = t * maxRadius
                        val px = center.x + cos(angle) * radius
                        val py = center.y + sin(angle) * radius
                        if (i == 0) spiralPath.moveTo(px, py) else spiralPath.lineTo(px, py)
                    }
                    drawPath(
                        path = spiralPath,
                        color = numeralColor.copy(alpha = 0.22f),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
            .drawWithContent {
                // Draw glowing squircle eye base and inner HH / MM numerals
                drawContent()

                val w = size.width
                val h = size.height
                val center = Offset(w * 0.5f, h * 0.5f)

                if (glitchIntensity > 0.01f) {
                    drawRect(
                        color = GlintCyan.copy(alpha = 0.28f * glitchIntensity),
                        topLeft = Offset(0f, h * 0.28f),
                        size = Size(w, 3.dp.toPx())
                    )
                    drawRect(
                        color = GlintMagenta.copy(alpha = 0.22f * glitchIntensity),
                        topLeft = Offset(0f, h * 0.68f),
                        size = Size(w, 2.dp.toPx())
                    )
                }

                // Skip top and bottom eyelid masks completely when expressions are turned off (NONE) and not blinking
                val skipEyelidMasks = currentEmotion == EmuFacialExpression.NONE && !isBlinking
                if (!skipEyelidMasks) {
                    val topDrop = expression.topLidDrop.coerceIn(0f, 1f)
                    val bottomRaise = expression.bottomLidRaise.coerceIn(0f, 1f)
                    val hasTopLid = topDrop > 0.005f || abs(expression.topArchCurve) > 0.005f
                    val hasBottomLid = bottomRaise > 0.005f || abs(expression.bottomArchCurve) > 0.005f

                    if (hasTopLid || hasBottomLid) {
                        rotate(degrees = signedBrowRotation, pivot = center) {
                            val bleedX = w * 0.45f
                            val bleedY = h * 0.45f

                            if (hasTopLid) {
                                // Positive topArchCurve creates an upward happy ^ arch;
                                // negative topArchCurve droops downward in the middle.
                                val positiveArch = expression.topArchCurve.coerceAtLeast(0f)
                                val wingDrop = h * (topDrop + 0.14f * positiveArch)
                                val archPull = h * 0.30f * expression.topArchCurve
                                val topPath = Path().apply {
                                    moveTo(-bleedX, -bleedY)
                                    lineTo(w + bleedX, -bleedY)
                                    lineTo(w + bleedX, wingDrop)
                                    quadraticTo(
                                        center.x,
                                        wingDrop - archPull,
                                        -bleedX,
                                        wingDrop
                                    )
                                    close()
                                }
                                drawPath(path = topPath, color = maskColor)
                            }

                            if (hasBottomLid) {
                                // Scale mid-level cheek raise smoothly so HH/MM remain crisp during Happy ^ ^
                                val effectiveBottomFactor = if (bottomRaise <= 0.6f) {
                                    bottomRaise * 0.62f
                                } else {
                                    0.372f + ((bottomRaise - 0.6f) / 0.4f) * 0.628f
                                }
                                val bottomEdgeY = h * (1f - effectiveBottomFactor)
                                val archPush = h * 0.24f * expression.bottomArchCurve
                                val bottomPath = Path().apply {
                                    moveTo(-bleedX, h + bleedY)
                                    lineTo(w + bleedX, h + bleedY)
                                    lineTo(w + bleedX, bottomEdgeY)
                                    quadraticTo(
                                        center.x,
                                        bottomEdgeY - archPush,
                                        -bleedX,
                                        bottomEdgeY
                                    )
                                    close()
                                }
                                drawPath(path = bottomPath, color = maskColor)
                            }
                        }
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onTapWink
            )
            .semantics {
                contentDescription = contentDescriptionText
            }
            .testTag(eyeTestTag),
        contentAlignment = Alignment.Center
    ) {
        // Nudge numeral gently away from rising smiling cheeks while keeping inertial lag
        val cheekNudgePx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (-expression.bottomLidRaise * 5f + expression.topLidDrop * 3f).dp.toPx()
        }
        Text(
            text = numerals,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                letterSpacing = (-1).sp
            ),
            color = numeralColor,
            modifier = Modifier
                .graphicsLayer {
                    translationX = laggedPupilXPx
                    translationY = laggedPupilYPx + cheekNudgePx
                }
                .testTag(numeralTestTag)
        )
    }
}
