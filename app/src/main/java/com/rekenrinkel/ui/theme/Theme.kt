package com.rekenrinkel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rekenrinkel.domain.model.Theme

private val LightColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.TextOnDark,
    primaryContainer = AppColors.PrimaryLight,
    secondary = AppColors.Accent,
    onSecondary = AppColors.TextOnDark,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.PrimaryLight,
    onPrimary = AppColors.TextPrimary,
    secondary = AppColors.Accent,
    onSecondary = AppColors.TextOnDark
)

/**
 * E-Ink color scheme for e-ink devices
 * High contrast, minimal colors, no gradients
 */
private val EInkColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color.DarkGray,
    onSecondary = Color.White,
    secondaryContainer = Color.LightGray,
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.LightGray,
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    outlineVariant = Color.DarkGray
)

@Composable
fun RekenRinkelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: Theme = Theme.DINOSAURS,
    einkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        einkMode -> EInkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // In e-ink mode, don't use colorful themes
    val themedColorScheme = if (einkMode) {
        colorScheme
    } else {
        colorScheme.copy(
            primary = when (appTheme) {
                Theme.DINOSAURS -> AppColors.DinosaurPrimary
                Theme.CARS -> AppColors.CarPrimary
                Theme.SPACE -> AppColors.SpacePrimary
            },
            secondary = when (appTheme) {
                Theme.DINOSAURS -> AppColors.DinosaurSecondary
                Theme.CARS -> AppColors.CarSecondary
                Theme.SPACE -> AppColors.SpaceSecondary
            }
        )
    }

    MaterialTheme(
        colorScheme = themedColorScheme,
        typography = AppTypography,
        content = content
    )
}