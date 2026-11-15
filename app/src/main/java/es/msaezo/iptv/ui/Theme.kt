package es.msaezo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF5EE6D0)
val Accent2 = Color(0xFFB18CFF)
val Tomato = Color(0xFFFA320A)
val Gold = Color(0xFFF5C518)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201B),
    secondary = Accent2,
    background = Color(0xFF0B1116),
    surface = Color(0xFF131C23),
    surfaceVariant = Color(0xFF1E2A33),
    onSurface = Color(0xFFE8EEF2),
    onSurfaceVariant = Color(0xFFAAB7C0),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0E3B43), Color(0xFF101820), Color(0xFF241A3A)),
                    )
                )
        ) { content() }
    }
}
