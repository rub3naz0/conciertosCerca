package com.rubenazo.buscaConciertos.application;

public record CreateArtistCommand(
    String name,
    String genre,
    String imageUrl,
    String website,
    String description
) {}
