package com.neurasamu.build.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neurasamu.build.data.ThemeMode

object SaMuRadius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(14.dp)
    val xl = RoundedCornerShape(22.dp)
    val full = CircleShape
}

@Immutable
data class SaMuColors(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceHover: Color,
    val onBackground: Color,
    val onSurface: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val userBubble: Color,
    val userBubbleBorder: Color,
    val assistantBubble: Color,
    val assistantBubbleBorder: Color,
    val error: Color,
    val errorBg: Color,
    val success: Color,
    val warning: Color,
    val codeBg: Color,
    val codeText: Color,
    val linkColor: Color
)

val SaMuDarkColors = SaMuColors(
    isDark = true,
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF0F1115),
    surface = Color(0xFF1A1D24),
    surfaceVariant = Color(0xFF252932),
    surfaceHover = Color(0xFF2F333D),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    divider = Color(0xFF2A2E36),
    userBubble = Color(0x30FF7A45),
    userBubbleBorder = Color(0x4DFF7A45),
    assistantBubble = Color(0xFF1A1D24),
    assistantBubbleBorder = Color(0xFF252932),
    error = Color(0xFFEF4444),
    errorBg = Color(0xFF3B1212),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFF59E0B),
    codeBg = Color(0xFF0A0C10),
    codeText = Color(0xFFE2E8F0),
    linkColor = Color(0xFFFF7A45)
)

val SaMuLightColors = SaMuColors(
    isDark = false,
    primary = Color(0xFFFF6B35),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    surfaceHover = Color(0xFFE2E8F0),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF64748B),
    textTertiary = Color(0xFF94A3B8),
    divider = Color(0xFFE2E8F0),
    userBubble = Color(0x33FF7A45),
    userBubbleBorder = Color(0x55FF6B35),
    assistantBubble = Color(0xFFFFFFFF),
    assistantBubbleBorder = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    errorBg = Color(0xFFFEE2E2),
    success = Color(0xFF16A34A),
    warning = Color(0xFFD97706),
    codeBg = Color(0xFF1E293B),
    codeText = Color(0xFFF8FAFC),
    linkColor = Color(0xFFFF6B35)
)

val LocalSaMuColors = staticCompositionLocalOf { SaMuDarkColors }
val LocalFontScale = compositionLocalOf { 1.0f }

@Composable
fun SamuTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val target = if (useDark) SaMuDarkColors else SaMuLightColors

    val animPrimary by animateColorAsState(target.primary, tween(250), label = "primary")
    val animBg by animateColorAsState(target.background, tween(250), label = "bg")
    val animSurface by animateColorAsState(target.surface, tween(250), label = "surface")
    val animSurfaceVar by animateColorAsState(target.surfaceVariant, tween(250), label = "surfaceVar")
    val animDivider by animateColorAsState(target.divider, tween(250), label = "divider")

    val resolved = target.copy(
        primary = animPrimary,
        background = animBg,
        surface = animSurface,
        surfaceVariant = animSurfaceVar,
        divider = animDivider
    )

    val m3Scheme = if (useDark) {
        darkColorScheme(
            primary = resolved.primary,
            onPrimary = resolved.onPrimary,
            background = resolved.background,
            onBackground = resolved.onBackground,
            surface = resolved.surface,
            onSurface = resolved.onSurface,
            surfaceVariant = resolved.surfaceVariant,
            onSurfaceVariant = resolved.textSecondary,
            error = resolved.error,
            outline = resolved.divider
        )
    } else {
        lightColorScheme(
            primary = resolved.primary,
            onPrimary = resolved.onPrimary,
            background = resolved.background,
            onBackground = resolved.onBackground,
            surface = resolved.surface,
            onSurface = resolved.onSurface,
            surfaceVariant = resolved.surfaceVariant,
            onSurfaceVariant = resolved.textSecondary,
            error = resolved.error,
            outline = resolved.divider
        )
    }

    CompositionLocalProvider(
        LocalSaMuColors provides resolved,
        LocalFontScale provides fontScale.coerceIn(0.8f, 1.4f)
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content = content
        )
    }
}
