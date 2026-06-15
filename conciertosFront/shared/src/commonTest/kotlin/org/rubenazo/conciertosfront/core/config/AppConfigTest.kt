package org.rubenazo.conciertosfront.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class AppConfigTest {

    // SCN-DBG-001: SHOW_DEBUG is a Boolean constant
    @Test
    fun showDebug_isBoolean() {
        assertIs<Boolean>(AppConfig.SHOW_DEBUG)
    }

    // SCN-DBG-002: SHOW_DEBUG is a const (compile-time constant — accessed at object level)
    @Test
    fun showDebug_isTrue() {
        assertTrue(AppConfig.SHOW_DEBUG, "AppConfig.SHOW_DEBUG must be true")
    }

    // Without a dev override, every platform must resolve to production
    @Test
    fun resolveBaseUrl_nullOverride_fallsBackToProduction() {
        assertEquals(AppConfig.PRODUCTION_BASE_URL, AppConfig.resolveBaseUrl(null))
    }

    @Test
    fun resolveBaseUrl_emptyOverride_fallsBackToProduction() {
        assertEquals(AppConfig.PRODUCTION_BASE_URL, AppConfig.resolveBaseUrl(""))
    }

    @Test
    fun resolveBaseUrl_blankOverride_fallsBackToProduction() {
        assertEquals(AppConfig.PRODUCTION_BASE_URL, AppConfig.resolveBaseUrl("   "))
    }

    // A dev override (e.g. the Mac's LAN IP) wins over production
    @Test
    fun resolveBaseUrl_devOverride_isUsedAsIs() {
        assertEquals("http://192.168.1.50:8080", AppConfig.resolveBaseUrl("http://192.168.1.50:8080"))
    }

    @Test
    fun productionBaseUrl_isTheRealBackend() {
        assertEquals("https://api.conciertoscerca.es", AppConfig.PRODUCTION_BASE_URL)
    }
}
