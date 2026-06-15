package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.SalaConciertoInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataPort;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SalaConciertoUseCase implements SalaConciertoInputPort {

    private final SalaConciertoRepositoryPort repo;
    private final SyncMetadataPort syncMeta;

    public SalaConciertoUseCase(SalaConciertoRepositoryPort repo, SyncMetadataPort syncMeta) {
        this.repo = repo;
        this.syncMeta = syncMeta;
    }

    @Override
    public boolean hasChanges(Instant since) {
        if (!syncMeta.shouldSync("salas-concierto")) return false;
        if (since == null) return true;
        return since.isBefore(syncMeta.getLastModified("salas-concierto"));
    }

    @Override
    public SyncResponse<SalaConcierto> getSalas(Instant since) {
        List<SalaConcierto> data = since == null ? repo.findAll() : repo.findModifiedAfter(since);
        return new SyncResponse<>(syncMeta.getLastModified("salas-concierto"), data);
    }
}
