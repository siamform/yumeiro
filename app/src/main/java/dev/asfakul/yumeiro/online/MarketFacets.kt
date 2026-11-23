package dev.asfakul.yumeiro.online

/**
 * MKT-FILTER-1 (owner list 9): the Discover filter's Source and Repository choices are built from the market
 * listing that was loaded, never from a fixed list. Pure, runs off the main thread.
 */
object MarketFacets {
    data class Facet(val key: String, val label: String, val count: Int)

    /** Where a listing came from, in the order the sheet shows them. */
    private val ORIGINS = linkedMapOf(
        "curated" to "Checked by Yumeiro",
        "community" to "Community list",
        "discovered" to "Found on GitHub",
        "user" to "Your repositories",
    )

    /** The same extension listed by several repositories counts once. */
    private fun same(e: RemoteExtension) = e.internalName.lowercase()

    fun originLabel(origin: String): String = ORIGINS[origin] ?: origin.replaceFirstChar { it.uppercase() }

    /** One facet per origin present (extensions counted once each). Empty when there is nothing to choose. */
    fun origins(items: List<RemoteExtension>): List<Facet> {
        val counts = items.groupBy { it.origin }.mapValues { (_, l) -> l.distinctBy { same(it) }.size }
        if (counts.size < 2) return emptyList()
        val known = ORIGINS.keys.filter { it in counts }
        val other = counts.keys.filter { it !in ORIGINS }.sorted()
        return (known + other).map { Facet(it, originLabel(it), counts.getValue(it)) }
    }

    /** One facet per repository (key = its URL, lowercased), most extensions first, then by name. */
    fun repositories(items: List<RemoteExtension>, origin: String? = null): List<Facet> =
        items.asSequence().filter { origin == null || it.origin == origin }
            .groupBy { it.repoUrl.trim().lowercase() }
            .map { (url, l) -> Facet(url, l.first().repoName.ifBlank { ExtensionFormats.repoNameFromUrl(url) }, l.distinctBy { it.key }.size) }
            .sortedWith(compareByDescending<Facet> { it.count }.thenBy { it.label.lowercase() })

    /** Repositories whose name or link contains [query]. */
    fun search(facets: List<Facet>, query: String): List<Facet> {
        val q = query.trim()
        return if (q.isEmpty()) facets else facets.filter { it.label.contains(q, true) || it.key.contains(q, true) }
    }

    /**
     * Applies the Source filter. With no source picked the market keeps its one card per extension (the
     * primary listing). With a source picked, an extension's copy from that source is shown even when its
     * primary card is from another one, still once per extension.
     */
    fun bySource(items: Sequence<RemoteExtension>, origin: String?, keepPrimaryOnly: (RemoteExtension) -> Boolean): Sequence<RemoteExtension> =
        if (origin == null) items.filter(keepPrimaryOnly)
        else items.filter { it.origin == origin }.distinctBy { same(it) }
}
