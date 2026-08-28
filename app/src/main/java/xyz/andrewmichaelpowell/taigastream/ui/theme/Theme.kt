//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val MintLight = Color(0xFF00C7BE)
val MintDark = Color(0xFF66D4CF)
val SecondarySystemBackgroundLight = Color(0xFFEFEFF4)
val SecondarySystemBackgroundDark = Color(0xFF1C1C1E)
val LabelLight = Color(0xFF000000)
val LabelDark = Color(0xFFFFFFFF)
val TertiaryLabelLight = Color(0xFF8E8E93)
val TertiaryLabelDark = Color(0xFF98989F)
val QuaternaryLabelLight = Color(0xFFC7C7CC)
val QuaternaryLabelDark = Color(0xFF48484A)

data class TaigaStreamColors(
    val mint: Color,
    val secondarySystemBackground: Color,
    val label: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
)

val LocalTaigaStreamColors = compositionLocalOf {
    TaigaStreamColors(
        mint = MintLight,
        secondarySystemBackground = SecondarySystemBackgroundLight,
        label = LabelLight,
        tertiaryLabel = TertiaryLabelLight,
        quaternaryLabel = QuaternaryLabelLight,
    )
}

private val LightColors = lightColorScheme(
    primary = MintLight,
    surfaceVariant = SecondarySystemBackgroundLight,
    onSurface = LabelLight,
)

private val DarkColors = darkColorScheme(
    primary = MintDark,
    surfaceVariant = SecondarySystemBackgroundDark,
    onSurface = LabelDark,
)

@Composable
fun TaigaStreamTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colors = if (isDark) DarkColors else LightColors
    val customColors = if (isDark) {
        TaigaStreamColors(
            mint = MintDark,
            secondarySystemBackground = SecondarySystemBackgroundDark,
            label = LabelDark,
            tertiaryLabel = TertiaryLabelDark,
            quaternaryLabel = QuaternaryLabelDark,
        )
    } else {
        TaigaStreamColors(
            mint = MintLight,
            secondarySystemBackground = SecondarySystemBackgroundLight,
            label = LabelLight,
            tertiaryLabel = TertiaryLabelLight,
            quaternaryLabel = QuaternaryLabelLight,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTaigaStreamColors provides customColors) {
        MaterialTheme(
            colorScheme = colors,
            content = content,
        )
    }
}
