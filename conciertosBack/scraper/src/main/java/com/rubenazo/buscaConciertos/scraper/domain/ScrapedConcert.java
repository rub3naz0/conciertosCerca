package com.rubenazo.buscaConciertos.scraper.domain;

import java.time.LocalDate;
import java.util.List;

public record ScrapedConcert(
    String id,
    String salaConciertoId,
    List<String> artistSlugs,
    LocalDate date,
    String time,
    String price,
    String sourceUrl,
    String venueName,
    String venueProvince,
    String artistName,
    String genre,
    String imageUrl,
    String venueHref
) {}
