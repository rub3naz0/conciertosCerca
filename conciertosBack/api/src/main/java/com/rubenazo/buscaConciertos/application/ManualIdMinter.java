package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.scraper.application.SlugUtils;

import java.time.LocalDate;

/**
 * Mints deterministic ids for manually created entities.
 * All ids begin with "manual-", making them disjoint from scraper-generated ids.
 */
final class ManualIdMinter {

    private ManualIdMinter() {}

    /**
     * Mints a sala id: {@code manual-sala-<slug(name)>-<slug(province)>}.
     */
    static String salaId(String name, String province) {
        return "manual-sala-" + SlugUtils.slugify(name) + "-" + SlugUtils.slugify(province);
    }

    /**
     * Mints an artist id: {@code manual-artist-<slug(name)>}.
     */
    static String artistId(String name) {
        return "manual-artist-" + SlugUtils.slugify(name);
    }

    /**
     * Mints a concert id deterministic from sala id + date:
     * {@code manual-<slug(salaId)>-<yyyy-MM-dd>}.
     * Using the full salaId slug keeps the id human-readable and unique per (sala, date).
     */
    static String concertId(String salaId, LocalDate date) {
        return "manual-" + SlugUtils.slugify(salaId) + "-" + date.toString();
    }
}
