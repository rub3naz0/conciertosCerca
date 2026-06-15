package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.SalaConcierto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalaConciertoRepositoryPort {
    List<SalaConcierto> findAll();
    List<SalaConcierto> findModifiedAfter(Instant since);
    List<SalaConcierto> findAllIncludingBlocked();
    Optional<SalaConcierto> findByIdIncludingBlocked(String id);
}
