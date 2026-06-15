package com.rubenazo.buscaConciertos.scraper.application.ports.out;

import java.util.Set;

public interface ExistingDataReaderPort {
    Set<String> existingConcertIds();
    Set<String> existingArtistIds();
    Set<String> enrichedArtistIds();
    Set<String> existingVenueIds();
}
