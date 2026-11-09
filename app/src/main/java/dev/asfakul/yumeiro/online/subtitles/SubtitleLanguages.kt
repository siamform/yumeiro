/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

/**
 * Language codes for the subtitle sites. Yumeiro keeps a two-letter ISO 639-1 code
 * (plus "pt-br" for Brazilian Portuguese); each site wants its own spelling:
 * SubDL uses upper-case codes ("EN", "BR_PT"), SubSource uses lower-case English names ("english").
 */
object SubtitleLanguages {
    /** ISO 639-1 → SubSource language name. */
    private val SUBSOURCE = mapOf(
        "en" to "english", "fa" to "farsi_persian", "af" to "afrikaans", "sq" to "albanian", "am" to "amharic",
        "ar" to "arabic", "hy" to "armenian", "az" to "azerbaijani", "eu" to "basque", "be" to "belarusian",
        "bn" to "bengali", "bs" to "bosnian", "pt-br" to "brazilian_portuguese", "bg" to "bulgarian", "my" to "burmese",
        "ca" to "catalan", "zh" to "chinese_bg_code", "hr" to "croatian", "cs" to "czech", "da" to "danish",
        "nl" to "dutch", "et" to "estonian", "fi" to "finnish", "fr" to "french", "ka" to "georgian",
        "de" to "german", "el" to "greek", "he" to "hebrew", "hi" to "hindi", "hu" to "hungarian",
        "is" to "icelandic", "id" to "indonesian", "ga" to "irish", "it" to "italian", "ja" to "japanese",
        "kn" to "kannada", "kk" to "kazakh", "km" to "khmer", "ko" to "korean", "ku" to "kurdish",
        "lv" to "latvian", "lt" to "lithuanian", "mk" to "macedonian", "ms" to "malay", "ml" to "malayalam",
        "mr" to "marathi", "mn" to "mongolian", "ne" to "nepali", "no" to "norwegian", "pl" to "polish",
        "pt" to "portuguese", "ps" to "pushto", "ro" to "romanian", "ru" to "russian", "sr" to "serbian",
        "si" to "sinhala", "sk" to "slovak", "sl" to "slovenian", "so" to "somali", "es" to "spanish",
        "sw" to "swahili", "sv" to "swedish", "tl" to "tagalog", "ta" to "tamil", "te" to "telugu",
        "th" to "thai", "tr" to "turkish", "uk" to "ukrainian", "ur" to "urdu", "uz" to "uzbek",
        "vi" to "vietnamese", "cy" to "welsh",
    )

    /** ISO 639-2 (both B and T forms) → ISO 639-1, for the local player's three-letter codes. */
    private val FROM_ISO3 = mapOf(
        "eng" to "en", "ben" to "bn", "hin" to "hi", "ara" to "ar", "urd" to "ur", "spa" to "es",
        "fre" to "fr", "fra" to "fr", "ger" to "de", "deu" to "de", "ita" to "it", "por" to "pt", "pob" to "pt-br",
        "rus" to "ru", "tur" to "tr", "ind" to "id", "may" to "ms", "msa" to "ms", "jpn" to "ja", "kor" to "ko",
        "chi" to "zh", "zho" to "zh", "per" to "fa", "fas" to "fa", "tha" to "th", "vie" to "vi", "dut" to "nl",
        "nld" to "nl", "pol" to "pl", "swe" to "sv", "ukr" to "uk", "heb" to "he", "gre" to "el", "ell" to "el",
        "tam" to "ta", "tel" to "te", "mal" to "ml", "kan" to "kn", "mar" to "mr", "nep" to "ne", "sin" to "si",
        "rum" to "ro", "ron" to "ro", "cze" to "cs", "ces" to "cs", "hun" to "hu", "fin" to "fi", "dan" to "da",
        "nor" to "no", "bul" to "bg", "hrv" to "hr", "srp" to "sr", "slo" to "sk", "slk" to "sk", "slv" to "sl",
    )

    /** Normalises "EN", "en_US", "pt-BR", "eng", "pob" … to Yumeiro's code ("en", "pt-br"). */
    fun normalize(code: String): String {
        val c = code.trim().lowercase().replace('_', '-')
        if (c == "pt-br" || c == "pob" || c == "br-pt") return "pt-br"
        if (c.length == 3) return FROM_ISO3[c] ?: c
        return c.substringBefore('-').ifBlank { "en" }
    }

    /** SubDL's code for [code] ("EN", "BR_PT", "ZH"). */
    fun subdl(code: String): String = when (val c = normalize(code)) {
        "pt-br" -> "BR_PT"
        else -> c.uppercase()
    }

    /** SubSource's language name for [code], or null when the site doesn't list it. */
    fun subsource(code: String): String? = SUBSOURCE[normalize(code)]
}
