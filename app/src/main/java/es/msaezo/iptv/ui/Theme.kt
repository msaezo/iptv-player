package es.msaezo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** TUTV brand red (our own shade). */
val Brand = Color(0xFFE8384F)
val Bg = Color(0xFF0A0A0A)
val Muted = Color(0xFFB3B3B3)
val Accent = Color(0xFF3FB8E8)
val Tomato = Color(0xFFFA320A)
val Gold = Color(0xFFF5C518)

private val scheme = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Color.White,
    background = Bg,
    surface = Color(0xFF161616),
    surfaceVariant = Color(0xFF232323),
    onSurface = Color.White,
    onSurfaceVariant = Muted,
    error = Color(0xFFFF6B6B),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().background(Bg)) { content() }
    }
}
