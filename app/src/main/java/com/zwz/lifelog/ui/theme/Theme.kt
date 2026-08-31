package com.zwz.lifelog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.zwz.lifelog.data.ThemePrefs

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B3273),
    secondary = Color(0xFF5A6472),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFE3EC),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1A1D21),
    surface = Color.White,
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFEFF1F4),
    onSurfaceVariant = Color(0xFF61656D),
    outline = Color(0xFFD6D9DE),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C6FF),
    onPrimary = Color(0xFF0B3273),
    primaryContainer = Color(0xFF1E3A6B),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFBFC7D4),
    onSecondary = Color(0xFF28303D),
    secondaryContainer = Color(0xFF2E3540),
    background = Color(0xFF101215),
    onBackground = Color(0xFFE4E7EB),
    surface = Color(0xFF181B20),
    onSurface = Color(0xFFE4E7EB),
    surfaceVariant = Color(0xFF23272E),
    onSurfaceVariant = Color(0xFFA9AEB6),
    outline = Color(0xFF333941),
    error = Color(0xFFFFB4AB)
)

@Composable
fun LifeLogTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode by ThemePrefs.mode(context).collectAsState(initial = "auto")
    val dynamic by ThemePrefs.dynamicColor(context).collectAsState(initial = true)

    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic && !dark -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}
