package com.rubenazo.buscaConciertos.application;

public record CreateSalaCommand(
    String name,
    String address,
    String city,
    String province,
    Double lat,
    Double lng,
    String imageUrl,
    String description
) {}
