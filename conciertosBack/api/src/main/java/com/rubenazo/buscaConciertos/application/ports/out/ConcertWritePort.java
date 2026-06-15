package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.Concert;

import java.time.LocalDate;

public interface ConcertWritePort {
    void upsert(Concert concert);
    int markDeleted(String concertId);
    int deleteBeforeDate(LocalDate cutoff);
}
