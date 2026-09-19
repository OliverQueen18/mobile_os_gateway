package com.osgateway.distributor.util

/** Résout une URL média (logo) relative à la base API. */
fun resolveMediaUrl(apiBaseUrl: String, pathOrUrl: String?): String? {
    if (pathOrUrl.isNullOrBlank()) return null
    val value = pathOrUrl.trim()
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
        return value
    }
    val root = apiBaseUrl.trimEnd('/')
        .removeSuffix("/api/v1")
        .removeSuffix("/api/v1/")
        .trimEnd('/')
    return root + if (value.startsWith("/")) value else "/$value"
}
