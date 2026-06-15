package com.rubenazo.buscaConciertos.application;

import java.util.List;

public record DataQualityCheckEvent(
    List<String> salaIds,
    List<String> artistIds
) {}
