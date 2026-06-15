package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.Concert;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Output port (driven side of the hexagon), read half: how the application reads concerts from
 * persistence without knowing it's SQLite. The concrete {@code ConcertSqliteAdapter} implements it.
 *
 * Reads and writes are split into separate ports ({@link ConcertWritePort} holds the mutations), a
 * CQRS-style boundary that lets a caller depend only on the capability it needs — e.g. a read-only
 * query handler never sees {@code upsert}/{@code delete}.
 */
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
