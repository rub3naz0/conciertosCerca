package com.rubenazo.buscaConciertos.scraper.domain;

public enum DiscrepancyType {
    FETCH_ERROR,
    PARSE_ERROR,
    SCHEMA_CHANGE,
    UNKNOWN_VENUE,
    ARTIST_NOT_FOUND,
    ID_COLLISION,
    EMPTY_FIELD,
    UNEXPECTED_CHANGE
}
