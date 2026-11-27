package dev.asfakul.yumeiro.ui.theme

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import androidx.compose.ui.graphics.Color

/**
 * TH-1: the accent colour of the current theme. GOLD is Yumeiro's moonlight gold (the theme's own accent,
 * unchanged). The others are curated Japanese colour names, each tuned for WCAG AA (4.5:1) text contrast on
 * the dark themes (light tone) and on the light themes (deep tone); `on` is the text drawn on the accent.
 * Material You keeps the system colours (the accent is not applied there). Also used as profile colours.
 */
enum class AccentTone(
    val label: String,
    val kana: String,
    internal val dark: Tone?,
    internal val light: Tone?,
) {
    GOLD(tr(R.string.tsuki_gold, "Tsuki gold"), "月", null, null),
    AO(tr(R.string.ao_blue, "Ao blue"), "青", Tone(0xFF7FB2FF, 0xFFA9CBFF, 0xFF4C86E0, 0xFF06142A), Tone(0xFF1F5FBF, 0xFF2F74D9, 0xFF174A96, 0xFFFFFFFF)),
    SAKURA(tr(R.string.sakura_pink, "Sakura pink"), "桜", Tone(0xFFFF9EC2, 0xFFFFC1D8, 0xFFE06A98, 0xFF2A0614), Tone(0xFFB0306A, 0xFFC94A82, 0xFF8A2252, 0xFFFFFFFF)),
    MATCHA(tr(R.string.matcha_green, "Matcha green"), "抹", Tone(0xFF9BD48A, 0xFFBDE6AF, 0xFF6BAF5A, 0xFF0E1F08), Tone(0xFF367028, 0xFF4A8A3A, 0xFF27551C, 0xFFFFFFFF)),
    FUJI(tr(R.string.fuji_violet, "Fuji violet"), "藤", Tone(0xFFB9A2FF, 0xFFD3C6FF, 0xFF8A6EE6, 0xFF160A33), Tone(0xFF5B3FC0, 0xFF7457D9, 0xFF452E99, 0xFFFFFFFF)),
    ;

    /** Swatch shown in pickers and as a profile's colour. */
    val swatch: Color get() = dark?.accent ?: Color(0xFFF5C46B)

    internal class Tone(accent: Long, bright: Long, second: Long, on: Long) {
        val accent = Color(accent); val bright = Color(bright); val second = Color(second); val on = Color(on)
    }

    companion object {
        fun from(name: String?): AccentTone = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GOLD
    }
}

/** The palette with [tone]'s accent family. Surfaces, text and scenes stay the theme's own. */
fun MtPalette.withAccent(tone: AccentTone): MtPalette {
    val t = (if (isDark) tone.dark else tone.light) ?: return this
    return copy(
        accent = t.accent, accentBright = t.bright, accent2 = t.second, onAccent = t.on,
        // Dark themes use the accent for the selected chip; light themes keep their ink chip.
        chipSelected = if (chipSelected == accent) t.accent else chipSelected,
        onChipSelected = if (chipSelected == accent) t.on else onChipSelected,
    )
}
