package com.geodnet.ntrip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SurveyColors {
    val RtkFixed = Color(0xFF10B981)      // Quality 4 (Survey RTK Fixed)
    val RtkFloat = Color(0xFFF59E0B)      // Quality 5 (RTK Float)
    val Dgps = Color(0xFF3B82F6)          // Quality 2 (DGPS)
    val Single = Color(0xFFEC4899)        // Quality 1 (Autonomous GNSS)
    val NoFix = Color(0xFF9CA3AF)         // Quality 0 (No Fix)

    val Connected = Color(0xFF10B981)
    val Connecting = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Disconnected = Color(0xFF9CA3AF)

    val Gps = Color(0xFF3B82F6)
    val Glonass = Color(0xFFEF4444)
    val Galileo = Color(0xFF8B5CF6)
    val Beidou = Color(0xFF10B981)
    val Qzss = Color(0xFFF59E0B)
    val Ssr = Color(0xFF06B6D4)
    val Station = Color(0xFF6366F1)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF00354E),
    primaryContainer = Color(0xFF004D71),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF00354E),
    secondaryContainer = Color(0xFF004D71),
    onSecondaryContainer = Color(0xFFC7E7FF),
    tertiary = Color(0xFF86EFAC),
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF005327),
    onTertiaryContainer = Color(0xFFA5F4B9),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF273549),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF475569),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF001E2F),
    secondary = Color(0xFF0369A1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF001E2F),
    tertiary = Color(0xFF15803D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF00220A),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainer = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

@Composable
fun NtripAppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
