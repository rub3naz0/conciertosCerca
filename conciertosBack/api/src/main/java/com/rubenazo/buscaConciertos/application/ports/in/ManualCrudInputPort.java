package com.rubenazo.buscaConciertos.application.ports.in;

import com.rubenazo.buscaConciertos.application.CreateArtistCommand;
import com.rubenazo.buscaConciertos.application.CreateConcertCommand;
import com.rubenazo.buscaConciertos.application.CreateSalaCommand;
import com.rubenazo.buscaConciertos.application.UpdateArtistCommand;
import com.rubenazo.buscaConciertos.application.UpdateConcertCommand;
import com.rubenazo.buscaConciertos.application.UpdateSalaCommand;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;

import java.util.List;

public interface ManualCrudInputPort {
    void deleteConcert(String concertId);
    SalaConcierto createSala(CreateSalaCommand cmd);
    Artist createArtist(CreateArtistCommand cmd);
    Concert createConcert(CreateConcertCommand cmd);
    SalaConcierto updateSala(String id, UpdateSalaCommand cmd);
    Artist updateArtist(String id, UpdateArtistCommand cmd);
    Concert updateConcert(String id, UpdateConcertCommand cmd);

    /**
     * Admin pre-fill / edit-target lookups — no eligibility filter (returns SEVERE-blocked
     * entities and, for concerts, soft-deleted rows too). 404 when absent.
     */
    SalaConcierto getSala(String id);
    Artist getArtist(String id);
    Concert getConcert(String id);

    /**
     * Admin list endpoints — include SEVERE-blocked entities; concerts exclude soft-deleted rows.
     * Used by the admin-web edit UI, never by `/api/v1`.
     */
    List<SalaConcierto> listSalas();
    List<Artist> listArtists();
    List<Concert> listConcerts();
}
