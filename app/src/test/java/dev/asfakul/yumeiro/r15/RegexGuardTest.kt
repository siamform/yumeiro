/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R15 Phase 1 guard: no regular expression may be compiled inside a function body, lambda or
 * getter (that is, per call — in composition, while a pager prefetches or a list scrolls). Every
 * pattern must be a top-level / object / companion constant, or come from `RegexCache` when it
 * depends on a runtime value. This test scans the source tree, so a new `Regex("…")` inside a
 * function fails the build with its file and line.
 */
class RegexGuardTest {
    private val pat = Regex("""(?<![A-Za-z0-9_.])(Regex\(|Pattern\.compile\()|\.toRegex\(""")
    private val rawLit = Regex("\"\"\"[\\s\\S]*?\"\"\"")
    private val stringLit = Regex(""""(?:\\.|[^"\\])*"""")
    private val charLit = Regex("""'(?:\\.|[^'\\])'""")
    private val fnBrace = Regex("""(\bfun\b|->|=\s*$|\b(if|else|when|for|while|try|catch|finally|run|let|apply|also|with|remember|LaunchedEffect|lazy|get)\b[^{]*$|\)\s*$)""")
    private val typeDecl = Regex("""\b(object|class|interface|enum class)\b""")

    /** Small helpers that only build a constant's pattern (called from property initialisers). */
    private val allowed = setOf(
        "dev/asfakul/yumeiro/text/RegexCache.kt",
        "dev/asfakul/yumeiro/online/AudioLanguages.kt:private fun word(",
        "dev/asfakul/yumeiro/online/ReleaseTags.kt:private fun r(",
    )

    @Test fun noRegexIsCompiledPerCall() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val hits = ArrayList<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (rel in allowed) return@forEach
            val stack = ArrayList<Boolean>() // true = function-like scope
            var pendingFn = false
            f.readLines().forEachIndexed { i, line ->
                val trimmed = line.trim()
                var code = rawLit.replace(line, "\"\"")
                code = stringLit.replace(code, "\"\"")
                code = charLit.replace(code, "''").substringBefore("//")
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && pat.containsMatchIn(line)) {
                    val inFn = stack.any { it } || Regex("""\bfun\b""").containsMatchIn(code)
                    val lazyInit = line.contains("by lazy")
                    val ok = allowed.any { a -> a.contains(':') && rel == a.substringBefore(':') && line.contains(a.substringAfter(':')) }
                    if (inFn && !lazyInit && !ok) hits += "$rel:${i + 1}: ${trimmed.take(140)}"
                }
                code.forEachIndexed { ci, ch ->
                    if (ch == '{') {
                        val before = code.substring(0, ci)
                        val isType = typeDecl.containsMatchIn(before) && !Regex("""\bfun\b""").containsMatchIn(before)
                        // A class / object body never sits inside a function here: if the brace
                        // tracking drifted (a brace in a multi-line string), recover at the type.
                        if (isType) while (stack.lastOrNull() == true) stack.removeAt(stack.size - 1)
                        stack += !isType && (pendingFn || fnBrace.containsMatchIn(before.trimEnd()) || before.isBlank() && stack.lastOrNull() == true)
                        pendingFn = false
                    } else if (ch == '}' && stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                }
                if (Regex("""\bfun\b""").containsMatchIn(code) && '{' !in code) pendingFn = true
            }
        }
        assertTrue("Regex compiled per call (hoist to a constant or use RegexCache):\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    /**
     * Hoisting moved each pattern's cost into its class's static initialiser, and that runs on
     * whichever thread touches the class first — the main thread, when the first touch is a
     * composable (run 43643387375: `YumixFeed$Category.<clinit>` and `YumixFilterKt.<clinit>` on
     * main). Every class that holds a pattern constant is therefore initialised by
     * [dev.asfakul.yumeiro.text.RegexWarmup] on a background thread; this keeps that list complete.
     */
    @Test fun everyPatternHolderIsWarmedOffMain() {
        val listed = dev.asfakul.yumeiro.text.RegexWarmup.CLASSES.toSet()
        val holders = patternHolders()
        val missing = holders.filterKeys { it !in listed }
        assertTrue("Add these to RegexWarmup.CLASSES:\n" + missing.entries.joinToString("\n") { "\"${it.key}\", // ${it.value}" }, missing.isEmpty())
        // Every listed name must be a real class, or the warm-up silently skips it.
        val bad = listed.filter { runCatching { Class.forName(it, false, javaClass.classLoader) }.isFailure }
        assertTrue("RegexWarmup.CLASSES names that do not exist: $bad", bad.isEmpty())
    }

    private val typeName = Regex("""\b(?:object|class|interface)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    private val companion = Regex("""\bcompanion\s+object\b""")
    private val jvmName = Regex("""@file:JvmName\("([^"]+)"\)""")
    private val pkgLine = Regex("""^package\s+([\w.]+)""")

    /** JVM class name → first source location, for every class with a pattern constant. */
    private fun patternHolders(): Map<String, String> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val out = LinkedHashMap<String, String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (rel == "dev/asfakul/yumeiro/text/RegexCache.kt") return@forEach
            val text = f.readText()
            val pkg = text.lineSequence().firstNotNullOfOrNull { pkgLine.find(it.trim())?.groupValues?.get(1) } ?: ""
            val facade = jvmName.find(text)?.groupValues?.get(1) ?: (f.nameWithoutExtension + "Kt")
            val stack = ArrayList<String?>() // type name for a class/object body, null for any other block
            var pendingType: String? = null
            f.readLines().forEachIndexed { i, line ->
                val trimmed = line.trim()
                var code = rawLit.replace(line, "\"\"")
                code = stringLit.replace(code, "\"\"")
                code = charLit.replace(code, "''").substringBefore("//")
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && pat.containsMatchIn(line) && !line.contains("by lazy")) {
                    val types = stack.filterNotNull().filter { it != "Companion" }
                    val name = if (types.isEmpty()) "$pkg.$facade" else "$pkg." + types.joinToString("$")
                    out.putIfAbsent(name, "$rel:${i + 1}")
                }
                code.forEachIndexed { ci, ch ->
                    if (ch == '{') {
                        val before = code.substring(0, ci)
                        val t = when {
                            companion.containsMatchIn(before) -> "Companion"
                            else -> typeName.findAll(before).lastOrNull()?.groupValues?.get(1)
                        } ?: pendingType?.takeIf { before.isBlank() || before.trimEnd().endsWith(")") || ':' in before }
                        stack += t
                        pendingType = null
                    } else if (ch == '}' && stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                }
                if ('{' !in code) {
                    val t = if (companion.containsMatchIn(code)) "Companion" else typeName.findAll(code).lastOrNull()?.groupValues?.get(1)
                    if (t != null && !Regex("""\bfun\b""").containsMatchIn(code)) pendingType = t
                }
            }
        }
        return out
    }
}
