package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensor.ParallaxState
import com.example.ui.theme.FlipCardInk
import com.example.ui.theme.PureAmoledBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FlipClockDisplay(
    parallaxState: ParallaxState,
    modifier: Modifier = Modifier
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            val millisUntilNextSecond = 1000L - (currentTimeMillis % 1000L)
            delay(millisUntilNextSecond.coerceAtLeast(100L))
        }
    }

    val date = remember(currentTimeMillis / 1000L) { Date(currentTimeMillis) }
    val hoursText = remember(currentTimeMillis / 60_000L) {
        SimpleDateFormat("hh", Locale.getDefault()).format(date)
    }
    val minutesText = remember(currentTimeMillis / 60_000L) {
        SimpleDateFormat("mm", Locale.getDefault()).format(date)
    }
    val amPmText = remember(currentTimeMillis / 60_000L) {
        SimpleDateFormat("a", Locale.getDefault()).format(date).uppercase(Locale.getDefault())
    }
    val dateText = remember(currentTimeMillis / 60_000L) {
        SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(date)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "colon_blink")
    val colonAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colon_alpha"
    )

    Column(
        modifier = modifier
            .testTag("flip_clock_display")
            .semantics(mergeDescendants = true) {
                contentDescription = "$hoursText:$minutesText $amPmText, $dateText"
            },
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            FlipClockPill(
                digits = hoursText,
                shadowOffsetX = parallaxState.clockShadowOffsetXDp,
                shadowOffsetY = parallaxState.clockShadowOffsetYDp
            )

            Text(
                text = ":",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .align(Alignment.CenterVertically)
                    .alpha(colonAlpha)
            )

            FlipClockPill(
                digits = minutesText,
                shadowOffsetX = parallaxState.clockShadowOffsetXDp,
                shadowOffsetY = parallaxState.clockShadowOffsetYDp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = amPmText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                letterSpacing = 0.4.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FlipClockPill(
    digits: String,
    shadowOffsetX: Dp,
    shadowOffsetY: Dp
) {
    val pillShape = RoundedCornerShape(8.dp)

    Box(contentAlignment = Alignment.Center) {
        // Dynamic opposite-tilt drop shadow plane
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 74.dp)
                .offset(x = shadowOffsetX, y = shadowOffsetY)
                .clip(pillShape)
                .background(PureAmoledBlack.copy(alpha = 0.55f))
                .blur(10.dp)
        )

        // Crisp white flip-style clock pill
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 74.dp)
                .clip(pillShape)
                .background(PureWhite),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = digits,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(220)) { -it / 2 } + fadeIn(tween(180)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(220)) { it / 2 } + fadeOut(tween(150))
                        )
                },
                label = "flip_digit"
            ) { currentDigits ->
                Text(
                    text = currentDigits,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.2).sp
                    ),
                    color = FlipCardInk
                )
            }

            // Hairline split-flap divider across the center
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x18121212))
            )
        }
    }
}
