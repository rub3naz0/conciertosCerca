package com.rubenazo.buscaConciertos.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TextSimilarityTest {

    @Test
    void identical_strings_return_1() {
        assertThat(TextSimilarity.nameSimilarity("Sala Apolo", "Sala Apolo")).isEqualTo(1.0);
    }

    @Test
    void case_normalization_returns_1() {
        assertThat(TextSimilarity.nameSimilarity("sala apolo", "SALA APOLO")).isEqualTo(1.0);
    }

    @Test
    void accent_stripping_returns_1() {
        assertThat(TextSimilarity.nameSimilarity("Sàlà Àpòlò", "Sala Apolo")).isEqualTo(1.0);
    }

    @Test
    void punctuation_strip_returns_1() {
        assertThat(TextSimilarity.nameSimilarity("Sala El Tren-ó!", "sala el tren o")).isEqualTo(1.0);
    }

    @Test
    void reordered_tokens_score_high_via_jaccard() {
        double score = TextSimilarity.nameSimilarity("Apolo Sala", "Sala Apolo");
        assertThat(score).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void typo_within_token_score_high_via_levenshtein() {
        double score = TextSimilarity.nameSimilarity("Sala Apolo", "Sala Apollo");
        assertThat(score).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void empty_first_argument_returns_0() {
        assertThat(TextSimilarity.nameSimilarity("", "Sala Apolo")).isEqualTo(0.0);
    }

    @Test
    void empty_second_argument_returns_0() {
        assertThat(TextSimilarity.nameSimilarity("Sala Apolo", "")).isEqualTo(0.0);
    }

    @Test
    void all_results_clamped_to_0_1() {
        double score = TextSimilarity.nameSimilarity("abc", "xyz");
        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    void both_empty_returns_0() {
        assertThat(TextSimilarity.nameSimilarity("", "")).isEqualTo(0.0);
    }

    // --- Edge-case coverage (Judgment Day 2026-06-08) ---

    @Test
    void null_first_argument_returns_0() {
        assertThat(TextSimilarity.nameSimilarity(null, "Sala Apolo")).isEqualTo(0.0);
    }

    @Test
    void null_second_argument_returns_0() {
        assertThat(TextSimilarity.nameSimilarity("Sala Apolo", null)).isEqualTo(0.0);
    }

    @Test
    void both_null_returns_0() {
        assertThat(TextSimilarity.nameSimilarity(null, null)).isEqualTo(0.0);
    }

    // Combined accent + punctuation + single-char typo still scores high.
    @Test
    void accent_punctuation_and_typo_combined_scores_high() {
        double score = TextSimilarity.nameSimilarity("Sàlà Apòlò!", "Sala Apollo");
        assertThat(score).isGreaterThanOrEqualTo(0.8);
    }

    // Names that normalize to punctuation/whitespace-only become empty → 0.0.
    @Test
    void punctuation_only_name_returns_0() {
        assertThat(TextSimilarity.nameSimilarity("!!! ---", "Sala Apolo")).isEqualTo(0.0);
    }

    // DOCUMENTS max(levenshtein, jaccard) bias: very short single-token names
    // differing by one char score high (~0.667), inflating false-positive risk.
    // Consumers must apply a length-aware threshold; the score alone is not enough.
    @Test
    void short_single_token_one_char_diff_scores_inflated() {
        double score = TextSimilarity.nameSimilarity("Sol", "Son");
        assertThat(score).isGreaterThan(0.6);
    }
}
