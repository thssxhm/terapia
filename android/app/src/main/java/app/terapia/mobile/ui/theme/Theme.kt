package app.terapia.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta lilás + azul claro
val Lilac = Color(0xFF8E6CCF)
val LilacLight = Color(0xFFEDE5FA)
val LilacDark = Color(0xFFCDB8F5)
val Sky = Color(0xFF5FA8DE)
val SkyLight = Color(0xFFE1F1FC)
val SkyDark = Color(0xFFA8D5F7)
val EmergencyYellow = Color(0xFFF9C623)
val EmergencyRed = Color(0xFFE53935)

private val LightColors = lightColorScheme(
    primary = Lilac,
    onPrimary = Color.White,
    primaryContainer = LilacLight,
    onPrimaryContainer = Color(0xFF2E1A57),
    secondary = Sky,
    onSecondary = Color.White,
    secondaryContainer = SkyLight,
    onSecondaryContainer = Color(0xFF0E3550),
    tertiary = Color(0xFFB98AD9),
    background = Color(0xFFFBF9FF),
    onBackground = Color(0xFF1E1B26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1B26),
    surfaceVariant = Color(0xFFF1ECF9),
    onSurfaceVariant = Color(0xFF4A4458),
    surfaceContainer = Color(0xFFF5F1FC),
    surfaceContainerHigh = Color(0xFFEFEAF8),
    outline = Color(0xFF7A7289),
    error = EmergencyRed,
)

private val DarkColors = darkColorScheme(
    primary = LilacDark,
    onPrimary = Color(0xFF2E1A57),
    primaryContainer = Color(0xFF4A3478),
    onPrimaryContainer = Color(0xFFEDE5FA),
    secondary = SkyDark,
    onSecondary = Color(0xFF0E3550),
    secondaryContainer = Color(0xFF1F4A6B),
    onSecondaryContainer = Color(0xFFE1F1FC),
    tertiary = Color(0xFFD9B8F0),
    background = Color(0xFF17141F),
    onBackground = Color(0xFFE8E1F2),
    surface = Color(0xFF1E1A28),
    onSurface = Color(0xFFE8E1F2),
    surfaceVariant = Color(0xFF2B2537),
    onSurfaceVariant = Color(0xFFCBC2DA),
    surfaceContainer = Color(0xFF241F30),
    surfaceContainerHigh = Color(0xFF2B2537),
    outline = Color(0xFF948BA3),
    error = Color(0xFFFF6E6A),
)

/** theme = "system" | "light" | "dark" */
@Composable
fun TerapiaTheme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
