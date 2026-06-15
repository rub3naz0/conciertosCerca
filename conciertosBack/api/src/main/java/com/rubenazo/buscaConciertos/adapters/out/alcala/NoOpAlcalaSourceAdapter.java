package com.rubenazo.buscaConciertos.adapters.out.alcala;

import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSnapshot;
import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSourcePort;

import java.time.LocalDate;
import java.util.List;

class NoOpAlcalaSourceAdapter implements AlcalaSourcePort {

    @Override
    public AlcalaSnapshot fetch(LocalDate from, LocalDate to) {
        return new AlcalaSnapshot(List.of(), List.of(), List.of());
    }
}
