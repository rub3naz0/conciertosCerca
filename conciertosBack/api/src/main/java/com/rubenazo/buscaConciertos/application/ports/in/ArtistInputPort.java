package com.rubenazo.buscaConciertos.application.ports.in;

import com.rubenazo.buscaConciertos.application.SyncResponse;
import com.rubenazo.buscaConciertos.domain.Artist;

import java.time.Instant;

public interface ArtistInputPort {
    boolean hasChanges(Instant since);
    SyncResponse<Artist> getArtists(Instant since);
}
