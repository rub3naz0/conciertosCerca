package org.rubenazo.conciertosfront.core.config

object AppConfig {
    const val SHOW_DEBUG = true

    /** Single source of truth for the backend URL — both platforms resolve against this. */
    const val PRODUCTION_BASE_URL = "https://api.conciertoscerca.es"

    val BASE_URL: String = resolveBaseUrl(platformDevBaseUrl)

    fun resolveBaseUrl(devOverride: String?): String =
        devOverride?.takeIf { it.isNotBlank() } ?: PRODUCTION_BASE_URL
}

/**
 * Optional dev-only backend override; null or blank means production.
 *
 * Needed because a local backend is reached differently per platform: the Android
 * emulator sees the host as 10.0.2.2, while an iOS simulator uses localhost and a
 * physical iPhone needs the Mac's LAN IP.
 */
expect val platformDevBaseUrl: String?
