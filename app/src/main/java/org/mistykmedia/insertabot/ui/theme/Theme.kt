package org.mistykmedia.insertabot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Mistyk Media: teal actions, coral accents, neutral reading surfaces.
// Error roles remain separate from the decorative coral secondary role.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF76D7CC), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048), onPrimaryContainer = Color(0xFF94F4E8),
    secondary = Color(0xFFFFB4A8), onSecondary = Color(0xFF561E16),
    secondaryContainer = Color(0xFF723329), onSecondaryContainer = Color(0xFFFFDAD3),
    tertiary = Color(0xFFA5CCDF), onTertiary = Color(0xFF073544),
    tertiaryContainer = Color(0xFF254C5B), onTertiaryContainer = Color(0xFFC1E8FC),
    background = Color(0xFF101414), onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF101414), onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBFC9C6),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF181C1C),
    surfaceContainer = Color(0xFF1C2020),
    surfaceContainerHigh = Color(0xFF272B2A),
    surfaceContainerHighest = Color(0xFF323635),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4947),
    inverseSurface = Color(0xFFE0E3E1), inverseOnSurface = Color(0xFF2D3130),
    inversePrimary = Color(0xFF006B60), surfaceTint = Color(0xFF76D7CC),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B60), onPrimary = Color.White,
    primaryContainer = Color(0xFF94F4E8), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF904A3F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD3), onSecondaryContainer = Color(0xFF3A0904),
    tertiary = Color(0xFF3E6373), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1E8FC), onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF5FAF8), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FAF8), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E1), onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF4F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3E9E6),
    surfaceContainerHighest = Color(0xFFDEE4E1),
    outline = Color(0xFF6F7976), outlineVariant = Color(0xFFBFC9C6),
    inverseSurface = Color(0xFF2D3130), inverseOnSurface = Color(0xFFEDF2EF),
    inversePrimary = Color(0xFF76D7CC), surfaceTint = Color(0xFF006B60),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val InsertaBotShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Follow system brightness; use Mistyk Media colors unless dynamic color is requested. */
@Composable
fun InsertaBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = InsertaBotTypography,
        shapes = InsertaBotShapes,
        content = content
    )
}
