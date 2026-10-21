package com.doublesail.scheduleboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4E7043), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8ECB3), onPrimaryContainer = Color(0xFF123E04),
    secondary = Color(0xFF596248), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7EFC9), onSecondaryContainer = Color(0xFF414A32),
    tertiary = Color(0xFF386662), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBBECE6), onTertiaryContainer = Color(0xFF1E4E4A),
    background = Color(0xFFF9FAEF), onBackground = Color(0xFF1A1C16),
    surface = Color.White, onSurface = Color(0xFF123E04),
    surfaceVariant = Color(0xFFE2E4D6), onSurfaceVariant = Color(0xFF45483D),
    outline = Color(0xFF75786C),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7D195), onPrimary = Color(0xFF24360D),
    primaryContainer = Color(0xFF3B4C22), onPrimaryContainer = Color(0xFFD4E9B4),
    secondary = Color(0xFFC1CAAB), onSecondary = Color(0xFF2C341E),
    secondaryContainer = Color(0xFF414A32), onSecondaryContainer = Color(0xFFDDE6C6),
    tertiary = Color(0xFFA0D0CA), onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF1E4E4A), onTertiaryContainer = Color(0xFFBBECE6),
    background = Color(0xFF12150E), onBackground = Color(0xFFE2E3D8),
    surface = Color(0xFF12150E), onSurface = Color(0xFFE2E3D8),
    surfaceVariant = Color(0xFF45483D), onSurfaceVariant = Color(0xFFC5C8B9),
    outline = Color(0xFF8F9285),
)

@Composable
fun ScheduleBoardTheme(
    settings: BoardSettings,
    onDarkTheme: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    SideEffect { onDarkTheme(dark) }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
