package com.rubenazo.buscaConciertos.application.ports.out;

import java.time.Instant;

public interface SyncMetadataPort {
    boolean shouldSync(String resource);
    Instant getLastModified(String resource);
}
