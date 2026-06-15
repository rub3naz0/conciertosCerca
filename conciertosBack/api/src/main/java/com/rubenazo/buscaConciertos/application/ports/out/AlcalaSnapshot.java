package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;

import java.util.List;

public record AlcalaSnapshot(
    List<SalaConcierto> venues,
    List<Artist> artists,
    List<Concert> concerts
) {}
