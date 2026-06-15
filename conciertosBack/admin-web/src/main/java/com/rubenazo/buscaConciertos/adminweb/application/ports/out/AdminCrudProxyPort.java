package com.rubenazo.buscaConciertos.adminweb.application.ports.out;

import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ArtistProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ConcertProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.SalaConciertoProxyDto;

import java.util.List;
import java.util.Map;

public interface AdminCrudProxyPort {
    void deleteConcert(String id);
    SalaConciertoProxyDto createSala(Map<String, Object> body);
    ArtistProxyDto createArtist(Map<String, Object> body);
    ConcertProxyDto createConcert(Map<String, Object> body);
    List<ConcertProxyDto> listConcerts();
    List<SalaConciertoProxyDto> listSalas();
    List<ArtistProxyDto> listArtists();

    // --- Edit (PUT) ---
    SalaConciertoProxyDto updateSala(String id, Map<String, Object> body);
    ArtistProxyDto updateArtist(String id, Map<String, Object> body);
    ConcertProxyDto updateConcert(String id, Map<String, Object> body);

    // --- Pre-fill (GET by id, including blocked/deleted) ---
    SalaConciertoProxyDto getSala(String id);
    ArtistProxyDto getArtist(String id);
    ConcertProxyDto getConcert(String id);

    // --- Admin lists including blocked entities ---
    List<SalaConciertoProxyDto> listSalasIncludingBlocked();
    List<ArtistProxyDto> listArtistsIncludingBlocked();
    List<ConcertProxyDto> listConcertsIncludingBlocked();
}
