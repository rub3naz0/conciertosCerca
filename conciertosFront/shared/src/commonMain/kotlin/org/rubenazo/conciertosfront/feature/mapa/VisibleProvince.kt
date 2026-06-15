package org.rubenazo.conciertosfront.feature.mapa

import org.rubenazo.conciertosfront.core.domain.model.Concert

/**
 * Returns the province of the first concert whose sala has both latitude and
 * longitude set, mirroring exactly how [MapaScreen] builds the list of
 * concerts rendered below the map (`concertsWithCoords`).
 *
 * Returns null if [concerts] contains no concert with coordinates.
 */
fun firstVisibleProvince(concerts: List<Concert>): String? {
    return concerts
        .firstOrNull { it.salaConcierto.lat != null && it.salaConcierto.lng != null }
        ?.salaConcierto
        ?.province
}
