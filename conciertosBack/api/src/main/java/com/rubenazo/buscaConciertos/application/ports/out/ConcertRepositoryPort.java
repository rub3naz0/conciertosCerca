package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.Concert;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConcertRepositoryPort {
    List<Concert> findAll();
    List<Concert> findAllIncludingBlocked();
    List<Concert> findModifiedAfter(Instant since);
    List<String> getDeletedIds(Instant since);
    boolean existsActiveById(String id);

    /**
     * Looks up a concert by id with no deleted/eligibility filter — returns
     * soft-deleted (deleted=1) and SEVERE-blocked concerts too. Used for the
     * admin edit flow's 404/deleted guard and pre-fill.
     */
    Optional<Concert> findByIdIncludingDeleted(String id);
}
