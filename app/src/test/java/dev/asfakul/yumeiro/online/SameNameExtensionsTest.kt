package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PIN-2: the same extension name from two repositories showed once (buildProviders kept one per name).
 * Old code: uniqueNames does not exist (compile failure) and the second provider was dropped.
 */
class SameNameExtensionsTest {
    private data class P(val name: String, val installedAt: Long, val repo: String?)

    private fun rename(list: List<P>) = ExtensionManager.uniqueNames(list, { it.name }, { it.installedAt }, { it.repo })
        .associate { it.first to it.second }

    @Test fun theFirstInstalledKeepsTheNameTheOtherGetsItsRepo() {
        val old = P("HiAnime", 100, "Phisher")
        val new = P("HiAnime", 200, "Cs-Kraptor")
        // order in the list must not matter: the oldest install keeps the name its history is under
        assertEquals(mapOf(new to "HiAnime · Cs-Kraptor"), rename(listOf(new, old)))
        assertEquals(mapOf(new to "HiAnime · Cs-Kraptor"), rename(listOf(old, new)))
    }

    @Test fun blankOrTakenLabelsGetANumber() {
        val a = P("Gogo", 1, "Repo")
        val b = P("Gogo", 2, null)
        val c = P("Gogo", 3, "Repo")
        val d = P("Gogo · Repo", 0, null) // a real provider already has that name
        val r = rename(listOf(a, b, c, d))
        assertEquals("Gogo · 2", r[b])
        assertEquals("Gogo · 3", r[c])
        assertEquals(setOf(b, c), r.keys)
    }

    @Test fun uniqueNamesAreLeftAlone() {
        assertEquals(emptyMap<P, String>(), rename(listOf(P("A", 1, "x"), P("B", 1, "x"))))
    }
}
