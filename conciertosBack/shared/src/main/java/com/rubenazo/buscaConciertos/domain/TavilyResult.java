package com.rubenazo.buscaConciertos.domain;

public record TavilyResult(
    String title,
    String url,
    String content,
    double score
) {}
