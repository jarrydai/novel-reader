package com.dyx.novelreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------- 品牌色 ----------------

/** 主色：墨绿，书卷气且不刺眼 */
private val Green = Color(0xFF2E7D5B)
private val GreenLight = Color(0xFF8AD9B4)
private val GreenContainerLight = Color(0xFFD3EFE2)
private val GreenContainerDark = Color(0xFF17402F)

/** 辅助色：赭石 */
private val Teal = Color(0xFFB07A3C)

/** 点缀色：暖棕 */
private val Indigo = Color(0xFF8C5A3B)

// ---------------- 浅色 ----------------

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = Color(0xFF0B3423),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F2EC),
    onSecondaryContainer = Color(0xFF04352F),
    tertiary = Indigo,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E0FF),
    onTertiaryContainer = Color(0xFF1C1250),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF10141B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10141B),
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = Color(0xFF59636F),
    outline = Color(0xFF8B93A1),
    outlineVariant = Color(0xFFE3E8F1),
    error = Color(0xFFD1453B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE2DF),
    onErrorContainer = Color(0xFF57120D),
)

// ---------------- 深色 ----------------

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF06214C),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFF63D6C6),
    onSecondary = Color(0xFF00332D),
    secondaryContainer = Color(0xFF0B4841),
    onSecondaryContainer = Color(0xFFC9F2EA),
    tertiary = Color(0xFFBDB1FF),
    onTertiary = Color(0xFF231660),
    tertiaryContainer = Color(0xFF382A80),
    onTertiaryContainer = Color(0xFFE6E1FF),
    background = Color(0xFF0A0E15),
    onBackground = Color(0xFFE8EDF6),
    surface = Color(0xFF121822),
    onSurface = Color(0xFFE8EDF6),
    surfaceVariant = Color(0xFF1A222E),
    onSurfaceVariant = Color(0xFF97A2B3),
    outline = Color(0xFF6C7A8C),
    outlineVariant = Color(0xFF283140),
    error = Color(0xFFFFB3AC),
    onError = Color(0xFF5C1109),
    errorContainer = Color(0xFF7C1F17),
    onErrorContainer = Color(0xFFFFE2DF),
)

// ---------------- 字体 ----------------

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
)

// ---------------- 形状 ----------------

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun NovelReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
