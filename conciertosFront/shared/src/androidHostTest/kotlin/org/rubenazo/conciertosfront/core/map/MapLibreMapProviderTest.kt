package org.rubenazo.conciertosfront.core.map

import kotlin.test.Test
import kotlin.test.assertTrue

class MapLibreMapProviderTest {

    // SCN-MAP-002: The tile style URL must point to OpenFreeMap (tiles.openfreemap.org)
    @Test
    fun tileStyleUrl_containsOpenFreeMapDomain() {
        assertTrue(
            actual = OPEN_FREE_MAP_DARK_STYLE_URL.contains("tiles.openfreemap.org"),
            message = "Tile style URL must contain tiles.openfreemap.org"
        )
    }

    // Triangulation: URL uses the 'liberty' dark style variant
    @Test
    fun tileStyleUrl_usesLibertyStyle() {
        assertTrue(
            actual = OPEN_FREE_MAP_DARK_STYLE_URL.contains("liberty"),
            message = "Tile style URL must reference the liberty (dark) style"
        )
    }
}
