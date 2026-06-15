package com.rubenazo.buscaConciertos.application.ports.in;

import java.time.LocalDate;

public interface SyncInputPort {
    void execute(String runId);
    void execute(String runId, LocalDate from, LocalDate to);
}
