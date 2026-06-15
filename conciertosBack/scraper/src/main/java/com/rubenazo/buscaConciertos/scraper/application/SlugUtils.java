package com.rubenazo.buscaConciertos.scraper.application;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern CONSECUTIVE_HYPHENS = Pattern.compile("-{2,}");
    private static final Pattern BOUNDARY_HYPHENS = Pattern.compile("^-+|-+$");

    private SlugUtils() {}

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        // Normalize unicode decomposition (NFD), strip diacritics, then NFC
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Lowercase
        String lower = normalized.toLowerCase();
        // Replace non-alphanumeric with hyphens
        String hyphenated = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");
        // Collapse consecutive hyphens
        String collapsed = CONSECUTIVE_HYPHENS.matcher(hyphenated).replaceAll("-");
        // Trim leading/trailing hyphens
        return BOUNDARY_HYPHENS.matcher(collapsed).replaceAll("");
    }

    public static String venueId(String province, String venueName) {
        return slugify(province) + "-" + slugify(venueName);
    }
}
