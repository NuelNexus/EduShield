package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryEspressoGold,
    onPrimary = Color(0xFF2E1E12),
    secondary = SecondaryEspressoMuted,
    onSecondary = OnSurfaceSugarCream,
    tertiary = TertiaryBronzeMuted,
    onTertiary = Color.White,
    background = BackgroundNightDark,
    onBackground = OnSurfaceSugarCream,
    surface = SurfaceEspressoDark,
    onSurface = OnSurfaceSugarCream,
    surfaceVariant = SecondaryEspressoMuted,
    onSurfaceVariant = OnSurfaceVariantLatte,
    outline = OutlineDarkEspresso,
    outlineVariant = OutlineDarkEspresso,
    error = ErrorAccentLightRed,
    onError = Color(0xFF420202),
    errorContainer = Color(0xFF8C1D1D),
    onErrorContainer = ErrorAccentLightRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryCoffeeBrown,
    onPrimary = Color.White,
    secondary = SecondaryWarmSand,
    onSecondary = OnSurfaceDeepBrown,
    tertiary = TertiaryGoldMuted,
    onTertiary = Color.White,
    background = BackgroundCream,
    onBackground = OnSurfaceDeepBrown,
    surface = SurfacePureWhite,
    onSurface = OnSurfaceDeepBrown,
    surfaceVariant = SecondaryWarmSand,
    onSurfaceVariant = OnSurfaceVariantNeutral,
    outline = OutlineBeige,
    outlineVariant = OutlineBeige,
    error = ErrorAccentRed,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = ErrorAccentRed
)

@Composable
fun EduGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent branding and color tones
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
        shapes = MaterialTheme.shapes
    )
}
