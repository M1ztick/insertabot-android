package org.mistykmedia.insertabot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Chat is long-form reading, so body text is tuned for it rather than left at
 * the Material defaults: a touch more line height, and body sizes that hold up
 * in a dense reply full of lists and code.
 */
private val DefaultTypography = Typography()

val InsertaBotTypography = Typography(
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = DefaultTypography.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = DefaultTypography.bodyMedium.copy(lineHeight = 22.sp),
    labelMedium = DefaultTypography.labelMedium.copy(letterSpacing = 0.4.sp)
)

/** Code blocks and inline code spans share one face so they read as one voice. */
val MonoTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp
)
