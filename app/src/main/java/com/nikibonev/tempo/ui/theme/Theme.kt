package com.nikibonev.tempo.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.nikibonev.tempo.data.model.AppSettings
import com.nikibonev.tempo.data.model.AppTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF6557E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E4FF),
    onPrimaryContainer = Color(0xFF221B68),
    secondary = Color(0xFF0B8F6A),
    onSecondary = Color.White,
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF1A1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFFECECF1),
    onSurfaceVariant = Color(0xFF5F6068),
    outline = Color(0xFFC5C5CD),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7ABFF),
    onPrimary = Color(0xFF30258A),
    primaryContainer = Color(0xFF4739B7),
    onPrimaryContainer = Color(0xFFE8E4FF),
    secondary = Color(0xFF62DDB2),
    onSecondary = Color(0xFF003829),
    background = Color(0xFF111216),
    onBackground = Color(0xFFE6E6EC),
    surface = Color(0xFF191B20),
    onSurface = Color(0xFFE6E6EC),
    surfaceVariant = Color(0xFF25272E),
    onSurfaceVariant = Color(0xFFC6C6CF),
    outline = Color(0xFF8E8E99),
    error = Color(0xFFFFB4AB),
)

@Composable
fun TempoTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = settings.theme == AppTheme.DARK
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = TempoTypography,
        content = content,
    )
}
