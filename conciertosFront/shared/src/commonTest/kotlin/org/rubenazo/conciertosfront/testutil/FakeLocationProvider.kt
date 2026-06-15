package org.rubenazo.conciertosfront.testutil

import org.rubenazo.conciertosfront.core.location.LocationPort
import org.rubenazo.conciertosfront.core.map.LatLng

class FakeLocationProvider(private val location: LatLng? = null) : LocationPort {
    var throwException: Boolean = false

    override suspend fun getLastLocation(): LatLng? {
        if (throwException) throw RuntimeException("Location error (fake)")
        return location
    }
}
