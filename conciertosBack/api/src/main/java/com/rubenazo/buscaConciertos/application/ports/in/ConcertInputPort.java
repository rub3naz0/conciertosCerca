package com.rubenazo.buscaConciertos.application.ports.in;

import com.rubenazo.buscaConciertos.application.ConcertSyncResponse;

import java.time.Instant;

public interface ConcertInputPort {
    boolean hasChanges(Instant since);
    ConcertSyncResponse getConcerts(Instant since);
}
