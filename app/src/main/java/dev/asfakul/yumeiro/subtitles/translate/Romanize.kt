/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.subtitles.translate

import android.os.Build

/**
 * B20 S6: subtitles in Latin letters. Bengali and Hindi use Yumeiro's own everyday spelling ("tumi ki korcho",
 * "kya kar rahe ho") on every Android version: ICU's rules are scholarly (ISO 15919, with diacritics) and need
 * Android 10. Other scripts (Russian, Arabic, Thai…) use ICU's Any-Latin on Android 10 and later.
 *
 * Known limits (documented in the plan): the inherent vowel is dropped at the end of a word and before a syllable
 * that carries its own vowel sign, which matches most everyday words but not every one (e.g. some loanwords);
 * spellings are colloquial, not one standard.
 */
object Romanize {
    /** Targets that can be shown in Latin letters on this device. */
    fun supports(lang: String): Boolean = when (lang) {
        "bn", "hi" -> true
        "ru", "ar", "th", "uk", "el", "ja", "ko", "zh", "ur", "fa", "ta", "te", "mr", "ne" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        else -> false
    }

    fun text(s: String, lang: String): String = when (lang) {
        "bn" -> indic(s, BENGALI)
        "hi", "mr", "ne" -> indic(s, DEVANAGARI)
        else -> icu(s) ?: s
    }

    private fun icu(s: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { ICU.transliterate(s) }.getOrNull()
    }

    private object ICU {
        private val t by lazy { android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII") }
        fun transliterate(s: String): String = synchronized(this) { t.transliterate(s) }
    }

    /** One Indic script: consonants, independent vowels, vowel signs and the vowel killer. */
    internal class Script(
        val consonants: Map<Char, String>,
        val vowels: Map<Char, String>,
        val signs: Map<Char, String>,
        val marks: Map<Char, String>,
        val digits: Char,
        val virama: Char,
        val inherent: String,
        val nukta: Char? = null,
        val nuktaForms: Map<Char, String> = emptyMap(),
        /** The consonant whose conjunct form doubles the one before it (Bengali য-phala); null = none. */
        val yaPhala: Char? = null,
        /** Bengali says the vowel after a word-final conjunct (অন্ত "onto"); Hindi doesn't (दोस्त "dost"). */
        val finalConjunctVowel: Boolean = false,
    )

    internal val BENGALI = Script(
        consonants = mapOf(
            'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "ng", 'চ' to "ch", 'ছ' to "ch", 'জ' to "j", 'ঝ' to "jh", 'ঞ' to "n",
            'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n", 'ত' to "t", 'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n",
            'প' to "p", 'ফ' to "f", 'ব' to "b", 'ভ' to "bh", 'ম' to "m", 'য' to "j", 'র' to "r", 'ল' to "l", 'শ' to "sh", 'ষ' to "sh",
            'স' to "s", 'হ' to "h", '\u09DC' to "r", '\u09DD' to "rh", '\u09DF' to "y",
        ),
        vowels = mapOf('অ' to "o", 'আ' to "a", 'ই' to "i", 'ঈ' to "i", 'উ' to "u", 'ঊ' to "u", 'ঋ' to "ri", 'এ' to "e", 'ঐ' to "oi", 'ও' to "o", 'ঔ' to "ou"),
        signs = mapOf('া' to "a", 'ি' to "i", 'ী' to "i", 'ু' to "u", 'ূ' to "u", 'ৃ' to "ri", 'ে' to "e", 'ৈ' to "oi", 'ো' to "o", 'ৌ' to "ou"),
        marks = mapOf('ং' to "ng", 'ঃ' to "h", 'ঁ' to "", 'ৎ' to "t", '।' to ".", '॥' to "."),
        digits = '০', virama = '্', inherent = "o",
        nukta = '়', nuktaForms = mapOf('ড' to "r", 'ঢ' to "rh", 'য' to "y"),
        yaPhala = 'য', finalConjunctVowel = true,
    )

    internal val DEVANAGARI = Script(
        consonants = mapOf(
            'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
            'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
            'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
            'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
        ),
        vowels = mapOf('अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"),
        signs = mapOf('ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o"),
        marks = mapOf('ं' to "n", 'ँ' to "n", 'ः' to "h", '।' to ".", '॥' to "."),
        digits = '०', virama = '्', inherent = "a",
        nukta = '़', nuktaForms = mapOf('क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z", 'ड' to "r", 'ढ' to "rh", 'फ' to "f"),
    )

    /**
     * Rule-based, word by word. A consonant keeps the inherent vowel unless: a vowel sign or the virama follows; it
     * ends the word (and the word has more than that one letter); or it follows a vowel and the next consonant has
     * its own vowel sign ("kor-cho", "kar-na").
     */
    internal fun indic(s: String, sc: Script): String {
        val out = StringBuilder()
        var i = 0
        val n = s.length
        // Positions of the letters in the current word, to know "word end" and "after a vowel".
        var lastWasVowel = false
        var afterVirama = false
        var prevCons = ""
        var letters = 0
        while (i < n) {
            val ch = s[i]
            val nk = sc.nukta != null && i + 1 < n && s[i + 1] == sc.nukta
            val cons = if (nk) sc.nuktaForms[ch] ?: sc.consonants[ch] else sc.consonants[ch]
            when {
                cons != null -> {
                    var j = i + 1 + if (nk) 1 else 0
                    // Bengali ্য after a consonant (ধন্য, বাক্য) doubles that consonant in speech; at a word's start it is silent.
                    if (sc.yaPhala != null && ch == sc.yaPhala && afterVirama) {
                        if (letters > 1) out.append(prevCons)
                    } else out.append(cons)
                    letters++
                    val conjunct = afterVirama
                    afterVirama = false
                    prevCons = cons
                    when {
                        j < n && s[j] == sc.virama -> { j++; lastWasVowel = false; afterVirama = true }
                        j < n && sc.signs.containsKey(s[j]) -> { out.append(sc.signs.getValue(s[j])); j++; lastWasVowel = true }
                        else -> {
                            val wordEnds = j >= n || !isLetter(s[j], sc)
                            val nextHasSign = !wordEnds && nextConsonantHasSign(s, j, sc)
                            // A word-final conjunct keeps its vowel (অন্ত "onto", যাচ্ছ "jachcho").
                            val drop = (wordEnds && letters > 1 && !(conjunct && sc.finalConjunctVowel)) || (lastWasVowel && nextHasSign)
                            if (!drop) out.append(sc.inherent)
                            lastWasVowel = !drop
                        }
                    }
                    i = j
                }
                sc.vowels.containsKey(ch) -> { out.append(sc.vowels.getValue(ch)); letters++; lastWasVowel = true; i++ }
                // A nasal or visarga closes the syllable: the next consonant keeps its vowel (ज़िंदगी "zindagi").
                sc.marks.containsKey(ch) -> { out.append(sc.marks.getValue(ch)); lastWasVowel = false; afterVirama = false; i++ }
                sc.signs.containsKey(ch) -> { out.append(sc.signs.getValue(ch)); lastWasVowel = true; i++ }
                ch == sc.virama || ch == sc.nukta || ch == '\u200C' || ch == '\u200D' -> i++
                ch in sc.digits..(sc.digits + 9) -> { out.append('0' + (ch - sc.digits)); i++ }
                else -> { out.append(ch); if (!ch.isLetter()) { letters = 0; lastWasVowel = false; afterVirama = false }; i++ }
            }
        }
        return out.toString()
    }

    private fun isLetter(c: Char, sc: Script) = sc.consonants.containsKey(c) || sc.vowels.containsKey(c) || sc.signs.containsKey(c) ||
        c == sc.virama || c == sc.nukta || (sc.marks.containsKey(c) && c != '।' && c != '॥')

    private fun nextConsonantHasSign(s: String, at: Int, sc: Script): Boolean {
        if (at >= s.length || !sc.consonants.containsKey(s[at])) return false
        var k = at + 1
        if (k < s.length && s[k] == sc.nukta) k++
        return k < s.length && sc.signs.containsKey(s[k])
    }
}
