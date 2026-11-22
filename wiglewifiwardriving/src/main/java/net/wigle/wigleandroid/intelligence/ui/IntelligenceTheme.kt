package net.wigle.wigleandroid.intelligence.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CyberBackground = Color(0xFF05070E)
val CyberSurface = Color(0xFF0C1425)
val CyberSurfaceVariant = Color(0xFF141F36)
val CyberBorder = Color(0xFF1F355C)
val CyberCyan = Color(0xFF00FFF2)
val CyberPink = Color(0xFFFF1F8F)
val CyberOrange = Color(0xFFFF7A00)
val CyberYellow = Color(0xFFFFD000)
val CyberGreen = Color(0xFF00FF66)
val CyberTextPrimary = Color(0xFFE2F0FD)
val CyberTextSecondary = Color(0xFF7E97B8)

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF003836),
    onPrimaryContainer = CyberCyan,
    secondary = CyberPink,
    onSecondary = Color.White,
    background = CyberBackground,
    onBackground = CyberTextPrimary,
    surface = CyberSurface,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextSecondary,
    outline = CyberBorder,
    error = CyberPink,
    onError = Color.White
)

val CyberTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = CyberCyan
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = CyberTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = CyberCyan
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = CyberTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = CyberTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = CyberTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        color = CyberCyan
    )
)

@Composable
fun IntelligenceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CyberTypography,
        content = content
    )
}

