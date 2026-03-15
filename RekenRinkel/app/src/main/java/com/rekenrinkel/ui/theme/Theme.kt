package com.rekenrinkel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun RekenRinkelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: Theme = Theme.DINOSAURS,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    // Pas thema aan op basis van geselecteerde stijl
    val themedColorScheme = colorScheme.copy(
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
    
    MaterialTheme(
        colorScheme = themedColorScheme,
        typography = AppTypography,
        content = content
    )
}