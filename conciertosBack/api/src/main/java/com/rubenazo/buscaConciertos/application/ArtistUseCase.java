package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.ArtistInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataPort;
import com.rubenazo.buscaConciertos.domain.Artist;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ArtistUseCase implements ArtistInputPort {

    private final ArtistRepositoryPort repo;
    private final SyncMetadataPort syncMeta;

    public ArtistUseCase(ArtistRepositoryPort repo, SyncMetadataPort syncMeta) {
        this.repo = repo;
        this.syncMeta = syncMeta;
    }

    @Override
    public boolean hasChanges(Instant since) {
        if (!syncMeta.shouldSync("artists")) return false;
        if (since == null) return true;
        return since.isBefore(syncMeta.getLastModified("artists"));
    }

    @Override
    public SyncResponse<Artist> getArtists(Instant since) {
        List<Artist> data = since == null ? repo.findAll() : repo.findModifiedAfter(since);
        return new SyncResponse<>(syncMeta.getLastModified("artists"), data);
    }
}
