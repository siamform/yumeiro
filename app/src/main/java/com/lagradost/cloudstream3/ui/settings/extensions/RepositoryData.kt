package com.lagradost.cloudstream3.ui.settings.extensions

import com.fasterxml.jackson.annotation.JsonProperty

/** A saved extension repository (CloudStream-compatible). */
data class RepositoryData(
    @JsonProperty("iconUrl") val iconUrl: String?,
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("description") val description: String? = null,
) {
    constructor(name: String, url: String) : this(null, name, url, null)
    constructor(iconUrl: String?, name: String, url: String) : this(iconUrl, name, url, null)
}
