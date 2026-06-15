package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.Artist;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArtistRepositoryPort {
    List<Artist> findAll();
    List<Artist> findModifiedAfter(Instant since);
    boolean existsById(String id);

    /**
     * Returns all artists, including those blocked by a non-terminal SEVERE
     * data_quality row (no eligibility filter). Used for admin listing/editing.
     */
    List<Artist> findAllIncludingBlocked();

    /**
     * Looks up an artist by id with no eligibility filter — returns SEVERE-blocked
     * artists too. Used for 404 checks and admin pre-fill in the edit flow.
     */
    Optional<Artist> findByIdIncludingBlocked(String id);
}
