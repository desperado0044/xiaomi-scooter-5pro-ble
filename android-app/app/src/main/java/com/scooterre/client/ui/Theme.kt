package com.scooterre.client.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A small, coherent Material 3 palette built around one accent (electric-scooter blue/teal)
// instead of the unstyled default MaterialTheme (flat white, no hierarchy). Light/dark variants
// share the same role structure so surfaces, text and the accent stay consistent either way.
private val AccentBlue = Color(0xFF0061A4)
private val AccentBlueDark = Color(0xFF9ECAFF)
private val Tertiary = Color(0xFF4CAF93)
private val TertiaryDark = Color(0xFF86D9BE)

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    tertiary = Tertiary,
    onTertiary = Color.White,
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7EDF5),
    onSurfaceVariant = Color(0xFF43474E),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = AccentBlueDark,
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF00497E),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    tertiary = TertiaryDark,
    onTertiary = Color(0xFF00382A),
    background = Color(0xFF10131A),
    surface = Color(0xFF1A1D24),
    surfaceVariant = Color(0xFF2A2F38),
    onSurfaceVariant = Color(0xFFC3C8D1),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ScooterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** SYSTEM follows the phone's dark-mode setting; LIGHT/DARK force one look. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** AUTO leaves rotation to Android as usual (so the phone's own rotation lock still applies);
 * PORTRAIT/LANDSCAPE pin the app to one orientation regardless of how the phone is held or
 * whether its rotation lock is on. Applies to the whole app (one Activity), not per screen. */
enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }
