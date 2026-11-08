package app.upmile.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.upmile.R

/** Tokens from the mockup (`cheotgil-v1.html` :root). Dark only. */
object Palette {
    val bg = Color(0xFF06080B)
    val surface = Color(0xFF0F1318)
    val surface2 = Color(0xFF161B22)
    val surface3 = Color(0xFF1D232C)
    val line = Color(0xFF242C37)
    val text = Color(0xFFEEF2F7)
    val sub = Color(0xFF9AA6B6)
    val dim = Color(0xFF5B6676)
    val accent = Color(0xFF4CC2FF)
    val accentSoft = accent.copy(alpha = .14f)
    val onAccent = Color(0xFF03141F)
    val mint = Color(0xFF6FF0C2)
    val warn = Color(0xFFFFB547)
    val ok = Color(0xFF5EE39A)
    val danger = Color(0xFFFF5A5A)
}

/** Asta Sans (42dot, OFL 1.1): one variable TTF, each weight pinned through the wght axis. */
@OptIn(ExperimentalTextApi::class) // resource Font with variationSettings
val AstaSans = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.asta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Car screen rule: no text under 18 sp. */
@Composable
fun UpmileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Palette.accent, background = Palette.bg, surface = Palette.surface)) {
        CompositionLocalProvider(
            LocalContentColor provides Palette.text,
            LocalTextStyle provides TextStyle(fontFamily = AstaSans, fontSize = 20.sp),
            content = content,
        )
    }
}
