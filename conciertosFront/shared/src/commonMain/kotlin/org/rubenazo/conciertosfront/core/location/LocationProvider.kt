package org.rubenazo.conciertosfront.core.location

import org.rubenazo.conciertosfront.core.map.LatLng

fun interface LocationPort {
    suspend fun getLastLocation(): LatLng?
}
