package com.osgateway.distributor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Couleurs du logo OS Gateway (navy + or) */
val OsNavy = Color(0xFF1B4F72)
val OsNavyDeep = Color(0xFF143A54)
val OsGold = Color(0xFFF4D03F)
val OsGoldSoft = Color(0xFFFFF6D6)
val OsBackground = Color(0xFFF3F7FB)
val OsSurface = Color(0xFFFFFFFF)
val OsMuted = Color(0xFF5F7383)
val OsBorder = Color(0xFFD7E2EC)
val OsShadow = Color(0x331B4F72)

// Compat aliases used by HomeScreen
val OsOrange = OsGold
val OsCharcoal = OsNavyDeep

private val Light = lightColorScheme(
    primary = OsNavy,
    onPrimary = Color.White,
    secondary = OsGold,
    onSecondary = OsNavyDeep,
    tertiary = OsNavyDeep,
    background = OsBackground,
    onBackground = OsNavyDeep,
    surface = OsSurface,
    onSurface = OsNavyDeep,
    surfaceVariant = OsGoldSoft,
    outline = OsBorder,
    error = Color(0xFFB00020),
)

private val Dark = darkColorScheme(
    primary = OsGold,
    onPrimary = OsNavyDeep,
    secondary = OsNavy,
    onSecondary = Color.White,
    background = Color(0xFF0E1A24),
    surface = Color(0xFF152636),
    onSurface = Color(0xFFE8EEF4),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, color = OsNavyDeep),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = OsNavyDeep),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, color = OsMuted),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun OsDistributorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
