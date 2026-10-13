package io.github.seventytwo.kitchensink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.seventytwo.kitchensink.model.Tone

/** The dashboard's colours, the same as the Windows client's. */
@Immutable
data class Palette(
    val bg: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val good: Color,
    val bad: Color,
    val warn: Color,
    val accent: Color,
    val frame: Color,
    val hover: Color,
    val rowAlt: Color,
    val dark: Boolean,
    /** The app's name and Recent's heading: text, except in the retro theme's yellow. */
    val heading: Color = text,
    /** Blocks' titles: muted, except in the retro theme's yellow. */
    val blockTitle: Color = muted,
    /** The background, top to bottom; a gradient in the retro theme. */
    val bgTop: Color = bg,
    val bgBottom: Color = bg,
    /** Panels' bevel, lit along the top and left; the retro theme's. */
    val bevelLight: Color = border,
    val bevelDark: Color = border,
    val retro: Boolean = false,
) {
    fun tone(t: Tone): Color = when (t) {
        Tone.Good -> good
        Tone.Bad -> bad
        Tone.Warn -> warn
        Tone.Muted -> muted
        Tone.Accent -> accent
        Tone.None -> text
    }

    companion object {
        private fun c(rgb: Long) = Color(0xFF000000 or rgb)

        val Dark = Palette(
            c(0x15171C), c(0x1E2128), c(0x2B2F38), c(0xE6E8EC), c(0x8C929D), c(0x3FB950),
            c(0xF8645A), c(0xD9A23A), c(0x5AA8FF), c(0x2A2E37), c(0x262A33), c(0x22252D), true,
        )
        val Light = Palette(
            c(0xF2F3F5), c(0xFFFFFF), c(0xE1E4E8), c(0x1F2328), c(0x656D76), c(0x1A7F37),
            c(0xCF222E), c(0x9A6700), c(0x0969DA), c(0xEEF0F3), c(0xF0F3F6), c(0xF7F8FA), false,
        )

        /**
         * After the Weather Channel's 90s WeatherStar screens: a deep blue sky
         * fading to purple, raised blue panels, white lettering with yellow
         * headings, and colours bright enough to read on blue.
         */
        val Retro = Palette(
            c(0x0B1663), c(0x1C3694), c(0x0A1450), c(0xFFFFFF), c(0xAEBCF4), c(0x6CF06C),
            c(0xFF6A5A), c(0xFF9A1F), c(0xFFC21A), c(0x132A7C), c(0x2B4CC0), c(0x2443A6), true,
            heading = c(0xFFE84A), blockTitle = c(0xFFE84A), bgTop = c(0x0B1663), bgBottom = c(0x3A1D7E),
            bevelLight = c(0x6F8FF0), bevelDark = c(0x06103C), retro = true,
        )

        /** Pixels showing black are off on an OLED screen, and dimmer, fainter static parts wear it less. */
        val Oled = Palette(
            c(0x000000), c(0x0A0B0D), c(0x17191D), c(0xC4C8CF), c(0x6A707A), c(0x2E9A45),
            c(0xD9534B), c(0xBF8A27), c(0x4A8AD4), c(0x131518), c(0x15171B), c(0x0E0F12), true,
        )
    }
}

/** Text styles and spacing, tighter in the compact layout. */
@Immutable
data class Metrics(
    val body: TextStyle,
    val caption: TextStyle,
    val heading: TextStyle,
    val big: TextStyle,
    val title: TextStyle,
    val gap: Dp,
    val cardPadH: Dp,
    val cardPadV: Dp,
    val cellPadH: Dp,
    val cellPadV: Dp,
) {
    companion object {
        /** The retro theme's lettering is all bold, with a black drop shadow. */
        fun of(compact: Boolean, scale: Float = 1f, retro: Boolean = false): Metrics {
            fun s(v: Float): TextUnit = (v * scale).sp
            fun d(v: Float): Dp = (v * scale).dp
            val shadow = if (retro) Shadow(Color.Black.copy(alpha = 0.82f), Offset(2.5f, 2.5f), 0f) else null
            val plain = if (retro) FontWeight.Bold else FontWeight.Normal
            val strong = if (retro) FontWeight.Bold else FontWeight.SemiBold
            fun style(size: Float, line: Float, weight: FontWeight) = TextStyle(fontSize = s(size), lineHeight = s(line), fontWeight = weight, shadow = shadow)
            return Metrics(
                body = style(15f, 20f, plain),
                caption = style(13f, 17f, plain),
                heading = style(15f, 20f, strong),
                big = style(24f, 30f, strong),
                title = style(19f, 24f, strong),
                gap = d(if (compact) 8f else 16f),
                cardPadH = d(if (compact) 14f else 18f),
                cardPadV = d(if (compact) 8f else 14f),
                cellPadH = d(if (compact) 6f else 8f),
                cellPadV = d(if (compact) 2f else 4f),
            )
        }
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.Dark }
val LocalMetrics = staticCompositionLocalOf { Metrics.of(compact = true) }

@Composable
fun KitchensinkTheme(palette: Palette, metrics: Metrics, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette, LocalMetrics provides metrics, content = content)
}
