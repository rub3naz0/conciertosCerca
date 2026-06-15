package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.Concert;

import java.time.LocalDate;

/**
 * Output port (driven side of the hexagon), write half: the concert mutations the application needs.
 * Paired with the read-only {@link ConcertRepositoryPort}; both are implemented by the same
 * {@code ConcertSqliteAdapter}. {@link #markDeleted} is a soft delete (sets {@code deleted=1}).
 */
public interface ConcertWritePort {
    void upsert(Concert concert);
    int markDeleted(String concertId);
    int deleteBeforeDate(LocalDate cutoff);
}
