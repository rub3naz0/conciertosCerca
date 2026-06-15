package com.rubenazo.buscaConciertos.application.ports.out;

import java.time.Instant;

public interface SyncMetadataWritePort {
    void updateLastModified(String resource, Instant timestamp);
}
