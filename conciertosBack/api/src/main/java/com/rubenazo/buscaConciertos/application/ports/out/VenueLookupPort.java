package com.rubenazo.buscaConciertos.application.ports.out;

import java.util.List;
import java.util.Optional;

public interface VenueLookupPort {
    Optional<VenueMatch> lookup(String name, String city, String province);

    default List<VenueLookupCandidate> lookupCandidates(String name, String city, String province, int limit) {
        return lookup(name, city, province)
            .map(VenueLookupCandidate::from)
            .map(List::of)
            .orElseGet(List::of);
    }
}
