package com.rubenazo.buscaConciertos.application;

import java.util.List;

public record CreateConcertCommand(
    String salaConciertoId,
    List<String> artistIds,
    String date,
    String time,
    String price
) {}
