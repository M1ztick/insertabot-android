package org.mistykmedia.insertabot.ui.theme

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

/**
 * Brand palette, sampled from the logo art in `drawable-nodpi/`.
 *
 * The mark is a neon robot lit in magenta and violet against near-black navy,
 * with pale cyan highlights. Those three are the roles here: magenta leads,
 * violet is the secondary, cyan is the accent that marks streaming and links.
 * Stock `darkColorScheme()` shares none of it, which is why the app looked
 * unrelated to its own icon.
 */
private val BrandMagenta = Color(0xFFF030C0)
private val BrandViolet = Color(0xFF9D4EFF)
private val BrandCyan = Color(0xFF7FD8F0)
private val BrandNavy = Color(0xFF150C33)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFADE4),
    onPrimary = Color(0xFF5B0049),
    primaryContainer = Color(0xFF81006A),
    onPrimaryContainer = Color(0xFFFFD8EE),
    secondary = Color(0xFFD5BAFF),
    onSecondary = Color(0xFF3D1A74),
    secondaryContainer = Color(0xFF55318D),
    onSecondaryContainer = Color(0xFFECDCFF),
    tertiary = BrandCyan,
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF004E5B),
    onTertiaryContainer = Color(0xFFB0EBFF),
    background = BrandNavy,
    onBackground = Color(0xFFE9E0F0),
    surface = BrandNavy,
    onSurface = Color(0xFFE9E0F0),
    // Assistant bubbles and code blocks sit on this, so it has to lift off the
    // background without competing with the primary-tinted user bubble.
    surfaceVariant = Color(0xFF2A2048),
    onSurfaceVariant = Color(0xFFC9BEDB),
    surfaceContainer = Color(0xFF221A3D),
    surfaceContainerHigh = Color(0xFF2C2349),
    outline = Color(0xFF938AA6),
    outlineVariant = Color(0xFF473D5E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFA4007F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8EE),
    onPrimaryContainer = Color(0xFF37002B),
    secondary = Color(0xFF6B45A6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECDCFF),
    onSecondaryContainer = Color(0xFF26005A),
    tertiary = Color(0xFF00677A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB0EBFF),
    onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFFFF7FB),
    onBackground = Color(0xFF1F1A24),
    surface = Color(0xFFFFF7FB),
    onSurface = Color(0xFF1F1A24),
    surfaceVariant = Color(0xFFF0E2EE),
    onSurfaceVariant = Color(0xFF4E444B),
    surfaceContainer = Color(0xFFF8EBF4),
    surfaceContainerHigh = Color(0xFFF2E4EE),
    outline = Color(0xFF80747C),
    outlineVariant = Color(0xFFD2C3CD)
)

/**
 * @param dynamicColor honour the wallpaper palette on Android 12+. On by
 *   default because a system-tinted app reads as native, but the brand scheme
 *   is the fallback everywhere below API 31.
 */
@Composable
fun InsertaBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        content = content
    )
}
