package app.banban.cut

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Toss (TDS) palette only, as in the MLOS UX guide: blue500 where there is no text on it,
 * blue700 under or as text so it reads at 4.5:1, greys for everything else.
 */
object Tds {
    val Grey50 = Color(0xFFF9FAFB)
    val Grey100 = Color(0xFFF2F4F6)
    val Grey200 = Color(0xFFE5E8EB)
    val Grey300 = Color(0xFFD1D6DB)
    val Grey500 = Color(0xFF8B95A1)
    val Grey600 = Color(0xFF6B7684)
    val Grey700 = Color(0xFF4E5968)
    val Grey900 = Color(0xFF191F28)
    val Blue50 = Color(0xFFE8F3FF)
    val Blue500 = Color(0xFF3182F6)
    val Blue700 = Color(0xFF1B64DA)
    val Red600 = Color(0xFFE42939)
    val White = Color.White
}

@Composable
fun BanbanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Tds.Blue700,
            onPrimary = Tds.White,
            primaryContainer = Tds.Blue50,
            onPrimaryContainer = Tds.Blue700,
            secondaryContainer = Tds.Grey100,
            onSecondaryContainer = Tds.Grey900,
            background = Tds.White,
            onBackground = Tds.Grey900,
            surface = Tds.White,
            onSurface = Tds.Grey900,
            surfaceVariant = Tds.Grey100,
            onSurfaceVariant = Tds.Grey600,
            surfaceContainer = Tds.White,
            surfaceContainerHigh = Tds.White,
            surfaceContainerHighest = Tds.Grey100,
            surfaceContainerLow = Tds.White,
            outline = Tds.Grey300,
            outlineVariant = Tds.Grey200,
            error = Tds.Red600,
        ),
        content = content,
    )
}
