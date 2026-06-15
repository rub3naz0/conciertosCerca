package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.SyncRun;

import java.util.Optional;

public interface SyncRunPort {
    String start();
    Optional<String> tryStart();
    void complete(String runId, int salas, int artists, int concerts, int errors, int discrepancies);
    void fail(String runId, String errorMessage);
    Optional<SyncRun> findLatest();
    Optional<SyncRun> findById(String runId);
    boolean isRunning();
}
