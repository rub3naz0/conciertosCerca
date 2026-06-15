package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupPort;
import com.rubenazo.buscaConciertos.application.ports.out.VenueMatch;

import java.util.Optional;

/**
 * No-op implementation of VenueLookupPort. Used when app.foursquare.api-key is blank.
 * Returns Optional.empty() for all lookups; never throws.
 */
class NoOpVenueLookupAdapter implements VenueLookupPort {

    @Override
    public Optional<VenueMatch> lookup(String name, String city, String province) {
        return Optional.empty();
    }
}
