package com.jarves.mh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Colibri Code palette — emerald-on-charcoal, matching the Colibri GPT mockups
// this fork's screens were designed against.
//
// IMPORTANT for anyone touching this file later: PocketOrange and PocketGreen
// are NOT free to repurpose. Across AgentScreen.kt, MarkdownText.kt,
// PocketDevApp.kt, SettingsScreenModern.kt, and TerminalScreen.kt,
// PocketOrange is already used ~150+ times as the general selection/accent
// color (selected borders, active tab text, step indicators) — not only for
// warnings — and PocketGreen ~20 times for success/confirmation states
// (checkmarks, "included", validation OK). Renaming or recoloring either one
// here silently changes the look of every one of those call sites. A real
// split into "brand accent" vs "warning" colors is worth doing, but it means
// walking each call site and choosing the right one deliberately — not a
// rename in this file. Until that happens, keep both names and both roles.
val PocketPrimary = Color(0xFF1D9E75) // brand accent: buttons, selection, links, active tabs
val PocketOrange = Color(0xFFF28C52) // unchanged: still the general accent color used across the app
val PocketGreen = Color(0xFF4FD1A5) // unchanged role: success/confirmation states; recolored to the emerald brand
val PocketRed = Color(0xFFC94F3E) // destructive confirmations (DestructiveCommandDialog)
val PocketBlue = Color(0xFF378ADD) // secondary accent (model badges etc.)
val PocketBackground = Color(0xFF0D1117)
val PocketSurface = Color(0xFF151B22)
val PocketSurfaceVariant = Color(0xFF1A1F27)
val PocketOutline = Color(0xFF232B34)

private val DarkColors = darkColorScheme(
    primary = PocketPrimary,
    onPrimary = Color(0xFF04342C),
    primaryContainer = Color(0xFF0F2E22),
    onPrimaryContainer = Color(0xFFEAFFF5),
    secondary = PocketBlue,
    onSecondary = Color(0xFF00203F),
    tertiary = PocketGreen,
    onTertiary = Color(0xFF00391E),
    error = PocketRed,
    onError = Color(0xFFFFFFFF),
    background = PocketBackground,
    onBackground = Color(0xFFF2F4F7),
    surface = PocketSurface,
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = PocketSurfaceVariant,
    onSurfaceVariant = Color(0xFFA9B2BF),
    outline = PocketOutline,
    outlineVariant = Color(0xFF2C353F),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF16805F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3F5E6),
    onPrimaryContainer = Color(0xFF04342C),
    secondary = Color(0xFF2C6FBF),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF9A6B0C),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFB3392A),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEFF5),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFD8DEE4),
)

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun PocketTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        content = content,
    )
}
