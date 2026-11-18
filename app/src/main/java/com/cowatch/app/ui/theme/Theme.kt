package com.cowatch.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Night = Color(0xFF140F1F)
val Surface1 = Color(0xFF1E1730)
val Surface2 = Color(0xFF2A2142)
val Rose = Color(0xFFFF5E7E)
val Violet = Color(0xFF7B5CFF)
val Mint = Color(0xFF4BE3B0)
val Muted = Color(0xFFB4A9CC)

val BrandGradient = Brush.linearGradient(listOf(Rose, Violet))

private val colors = darkColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    secondary = Violet,
    onSecondary = Color.White,
    tertiary = Mint,
    background = Night,
    onBackground = Color.White,
    surface = Surface1,
    onSurface = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Color(0xFF4A3F66),
    error = Color(0xFFFF8A80),
)

private val typography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

val CodeStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)

@Composable
fun CoWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Default text/icon colour for content not inside a Surface (otherwise black on our dark background).
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
