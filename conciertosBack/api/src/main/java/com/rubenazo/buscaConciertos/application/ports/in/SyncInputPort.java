package com.rubenazo.buscaConciertos.application.ports.in;

import java.time.LocalDate;

/**
 * Input port (driving side of the hexagon): the use-case contract the inbound adapters depend on.
 *
 * REST controllers in {@code adapters/in} call this interface, never the concrete {@link
 * com.rubenazo.buscaConciertos.application.SyncUseCase}. Every use case in {@code application} is
 * reached through an {@code application.ports.in} interface like this one, which keeps the delivery
 * mechanism (HTTP, scheduler, test) decoupled from the business logic.
 */
public interface SyncInputPort {
    void execute(String runId);
    void execute(String runId, LocalDate from, LocalDate to);
}
