package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val CharcoalColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = FlipCardInk,
    primaryContainer = ElevatedCharcoal,
    onPrimaryContainer = CrispOffWhite,
    secondary = SubtleLightGray,
    onSecondary = DeepCharcoal,
    secondaryContainer = ElevatedCharcoal,
    onSecondaryContainer = CrispOffWhite,
    tertiary = MutedGray,
    onTertiary = PureWhite,
    background = MatteCharcoal,
    onBackground = CrispOffWhite,
    surface = MatteCharcoal,
    onSurface = CrispOffWhite,
    surfaceVariant = ElevatedCharcoal,
    onSurfaceVariant = SubtleLightGray,
    surfaceContainerLowest = PureAmoledBlack,
    surfaceContainerLow = DeepCharcoal,
    surfaceContainer = ElevatedCharcoal,
    surfaceContainerHigh = ColorTokens.SheetSurface,
    outline = HairlineCharcoal,
    outlineVariant = ColorTokens.SubtleDivider
)

private val AmoledColorScheme = CharcoalColorScheme.copy(
    background = PureAmoledBlack,
    surface = PureAmoledBlack,
    surfaceContainerLow = PureAmoledBlack
)

private object ColorTokens {
    val SheetSurface = ElevatedCharcoal
    val SubtleDivider = HairlineCharcoal
}

val StillShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun StillLauncherTheme(
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (amoledBlack) AmoledColorScheme else CharcoalColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StillTypography,
        shapes = StillShapes,
        content = content
    )
}
