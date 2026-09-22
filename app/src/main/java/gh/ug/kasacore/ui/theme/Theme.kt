package gh.ug.kasacore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/*
 * High-contrast palette by construction (WCAG 2.1 AA, >= 4.5:1) — every color
 * pair below was picked as a foreground/background pair, not independently.
 * 03_FRONTEND_ANDROID_GUIDE.md Step 3: "contrast >= 4.5:1" for every control.
 */
val KasaGreen = Color(0xFF0B5D3B)       // on white: ~7.9:1
val KasaGreenLight = Color(0xFFDCEFE4)
val KasaDanger = Color(0xFFB3261E)      // on white: ~6.0:1
val KasaOnDark = Color(0xFFF5FFF9)

private val LightColors = lightColorScheme(
    primary = KasaGreen,
    onPrimary = Color.White,
    primaryContainer = KasaGreenLight,
    onPrimaryContainer = KasaGreen,
    error = KasaDanger,
    onError = Color.White,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF191C1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CDBA6),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0B5D3B),
    onPrimaryContainer = KasaGreenLight,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF191C1A),
    onBackground = KasaOnDark,
    surface = Color(0xFF191C1A),
    onSurface = KasaOnDark,
)

/** Large, scalable text everywhere — this app is used eyes-off or low-vision. */
val KasaBigButtonText = TextStyle(fontSize = 28.sp)
val KasaCaptionText = TextStyle(fontSize = 20.sp)

@Composable
fun KasaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
