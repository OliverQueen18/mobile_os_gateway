package com.osgateway.gateway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = GwGreen,
    onPrimary = Color.White,
    secondary = GwGold,
    onSecondary = GwGreenDeep,
    tertiary = GwGreenMid,
    background = GwMint,
    onBackground = GwGreenDeep,
    surface = GwSurface,
    onSurface = GwGreenDeep,
    surfaceVariant = GwGoldSoft,
    onSurfaceVariant = GwMuted,
    outline = GwBorder,
    error = GwDanger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = GwGold,
    onPrimary = GwGreenDeep,
    secondary = GwGreenMid,
    onSecondary = Color.White,
    tertiary = GwGoldSoft,
    background = Color(0xFF0A1A14),
    onBackground = Color(0xFFE8F0EC),
    surface = Color(0xFF13261E),
    onSurface = Color(0xFFE8F0EC),
    surfaceVariant = Color(0xFF1C3328),
    onSurfaceVariant = Color(0xFFA8BDB2),
    outline = Color(0xFF2F4A3C),
    error = Color(0xFFFF8A80),
    onError = GwGreenDeep,
)

@Composable
fun OsGatewayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = GwTypography,
        content = content,
    )
}
