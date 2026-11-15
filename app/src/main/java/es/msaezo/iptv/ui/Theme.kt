package es.msaezo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import es.msaezo.iptv.R

/** TUTV brand red (our own shade). Used sparingly: logo, progress, the selected-tab mark. */
val Brand = Color(0xFFE8384F)
val Bg = Color(0xFF0A0A0A)
/** Secondary text: light grey that stays readable on the black background (about 9:1). */
val Muted = Color(0xFFB8B8B8)
val Tomato = Color(0xFFFA320A)
val Gold = Color(0xFFF5C518)

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    background = Bg,
    onBackground = Color.White,
    surface = Color(0xFF161616),
    surfaceVariant = Color(0xFF232323),
    onSurface = Color.White,
    onSurfaceVariant = Muted,
    outline = Color(0xFF8A8A8A),
    error = Color(0xFFFF8A80),
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private val type = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Inter),
        displayMedium = t.displayMedium.copy(fontFamily = Inter),
        displaySmall = t.displaySmall.copy(fontFamily = Inter),
        headlineLarge = t.headlineLarge.copy(fontFamily = Inter),
        headlineMedium = t.headlineMedium.copy(fontFamily = Inter),
        headlineSmall = t.headlineSmall.copy(fontFamily = Inter),
        titleLarge = t.titleLarge.copy(fontFamily = Inter),
        titleMedium = t.titleMedium.copy(fontFamily = Inter),
        titleSmall = t.titleSmall.copy(fontFamily = Inter),
        bodyLarge = t.bodyLarge.copy(fontFamily = Inter),
        bodyMedium = t.bodyMedium.copy(fontFamily = Inter),
        bodySmall = t.bodySmall.copy(fontFamily = Inter),
        labelLarge = t.labelLarge.copy(fontFamily = Inter),
        labelMedium = t.labelMedium.copy(fontFamily = Inter),
        labelSmall = t.labelSmall.copy(fontFamily = Inter),
    )
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type) {
        Box(Modifier.fillMaxSize().background(Bg)) { content() }
    }
}
