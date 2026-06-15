package com.rubenazo.buscaConciertos.domain;

public record SearchOptions(String searchDepth, int maxResults) {

    public static SearchOptions defaults() {
        return new SearchOptions("basic", 5);
    }
}
