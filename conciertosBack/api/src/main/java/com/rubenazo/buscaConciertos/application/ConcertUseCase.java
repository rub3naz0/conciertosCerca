package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.ConcertInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataPort;
import com.rubenazo.buscaConciertos.domain.Concert;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ConcertUseCase implements ConcertInputPort {

    private final ConcertRepositoryPort repo;
    private final SyncMetadataPort syncMeta;

    public ConcertUseCase(ConcertRepositoryPort repo, SyncMetadataPort syncMeta) {
        this.repo = repo;
        this.syncMeta = syncMeta;
    }

    @Override
    public boolean hasChanges(Instant since) {
        if (!syncMeta.shouldSync("concerts")) return false;
        if (since == null) return true;
        return since.isBefore(syncMeta.getLastModified("concerts"));
    }

    @Override
    public ConcertSyncResponse getConcerts(Instant since) {
        List<Concert> data = since == null ? repo.findAll() : repo.findModifiedAfter(since);
        return new ConcertSyncResponse(syncMeta.getLastModified("concerts"), data, repo.getDeletedIds(since));
    }
}
