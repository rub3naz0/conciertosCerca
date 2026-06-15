package com.rubenazo.buscaConciertos.application;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Package-private utility for normalized text similarity.
 * Uses Levenshtein ratio and token Jaccard; takes max of both.
 * No external dependencies — hand-rolled per ADR-3.
 */
public class TextSimilarity {

    private TextSimilarity() {}

    /**
     * Returns a similarity score in [0.0, 1.0] between two strings.
     * Applies accent stripping, lowercasing, and punctuation normalization before comparison.
     * Returns 0.0 if either input is null or blank after normalization.
     */
    public static double nameSimilarity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return 0.0;
        }
        if (na.equals(nb)) {
            return 1.0;
        }
        double levRatio = levenshteinRatio(na, nb);
        double tokenJacc = tokenJaccard(na, nb);
        return clamp(Math.max(levRatio, tokenJacc), 0.0, 1.0);
    }

    /**
     * Normalizes a string:
     * lowercase → NFD + strip diacritic marks → replace non-alphanumeric runs with space → trim.
     */
    public static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase();
        // Decompose accents (NFD) then strip combining marks
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String stripped = decomposed.replaceAll("\\p{M}", "");
        // Replace any non-alphanumeric run (including punctuation, hyphens, etc.) with a space
        String spaced = stripped.replaceAll("[^a-z0-9]+", " ");
        return spaced.trim();
    }

    /**
     * Two-row iterative Levenshtein ratio: 1 - (distance / max(len_a, len_b)).
     */
    private static double levenshteinRatio(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int maxLen = Math.max(lenA, lenB);
        if (maxLen == 0) return 1.0;

        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                    Math.min(prev[j] + 1, curr[j - 1] + 1),
                    prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        int distance = prev[lenB];
        return 1.0 - (double) distance / maxLen;
    }

    /**
     * Token-set Jaccard similarity: |A∩B| / |A∪B|.
     * Tokens are whitespace-split; order independent; duplicates collapse.
     */
    private static double tokenJaccard(String a, String b) {
        Set<String> tokensA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> tokensB = new HashSet<>(Arrays.asList(b.split("\\s+")));

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);
        if (union.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        return (double) intersection.size() / union.size();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
