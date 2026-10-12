package com.rocioplata.familyfinaiapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorPrimaryFixedDim = Color(0xFF95D2C4)
private val ColorOnPrimaryFixed = Color(0xFF00201B)
private val ColorSecondaryFixedDim = Color(0xFF8FD4C4)
private val ColorTertiaryFixedDim = Color(0xFFFFB77A)
private val ColorOnTertiaryFixed = Color(0xFF2E1500)
private val ColorInverseSurface = Color(0xFF263331)
private val ColorInverseOnSurface = Color(0xFFE5F4F1)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = AppBackground,
    onBackground = OnSurface,
    surface = AppSurface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorPrimaryFixedDim,
    onPrimary = ColorOnPrimaryFixed,
    primaryContainer = Primary,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = ColorSecondaryFixedDim,
    onSecondary = ColorOnPrimaryFixed,
    secondaryContainer = Secondary,
    onSecondaryContainer = SecondaryContainer,
    tertiary = ColorTertiaryFixedDim,
    onTertiary = ColorOnTertiaryFixed,
    tertiaryContainer = Tertiary,
    onTertiaryContainer = TertiaryContainer,
    background = ColorInverseSurface,
    onBackground = ColorInverseOnSurface,
    surface = ColorInverseSurface,
    onSurface = ColorInverseOnSurface,
    surfaceVariant = OnSurfaceVariant,
    onSurfaceVariant = SurfaceVariant,
    outline = OutlineVariant,
    outlineVariant = Outline,
    error = ErrorContainer,
    onError = OnErrorContainer,
    errorContainer = Error,
    onErrorContainer = ErrorContainer
)

@Composable
fun FamilyFinAIAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
