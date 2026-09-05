package com.sellernest.poreceiving.network

import com.sellernest.poreceiving.BuildConfig

/**
 * The configured base URL, shown on the Sign In screen (§7.1: "Server:
 * prod-api.example.com... the first things support asks for"). Backed by
 * `BuildConfig.API_BASE_URL`, which is set per build type in `app/build.gradle.kts`
 * so debug and release point at different backends without a code change.
 */
object ApiConfig {
    const val BASE_PATH = "/api/mobile/receiving/"

    val baseUrl: String get() = BuildConfig.API_BASE_URL

    /** The bare host, for display on the Sign In screen — no scheme, no path. */
    val displayHost: String
        get() = baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
}
