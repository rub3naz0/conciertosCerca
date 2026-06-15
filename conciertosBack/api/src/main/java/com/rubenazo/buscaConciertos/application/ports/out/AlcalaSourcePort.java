package com.rubenazo.buscaConciertos.application.ports.out;

import java.time.LocalDate;

public interface AlcalaSourcePort {
    AlcalaSnapshot fetch(LocalDate from, LocalDate to);
}
