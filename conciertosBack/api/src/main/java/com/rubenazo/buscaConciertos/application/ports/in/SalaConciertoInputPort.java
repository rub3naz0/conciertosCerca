package com.rubenazo.buscaConciertos.application.ports.in;

import com.rubenazo.buscaConciertos.application.SyncResponse;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;

import java.time.Instant;

public interface SalaConciertoInputPort {
    boolean hasChanges(Instant since);
    SyncResponse<SalaConcierto> getSalas(Instant since);
}
