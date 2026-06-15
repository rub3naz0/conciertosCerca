package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Phase 4.5 — NoOpReverseGeocodingAdapter always returns Optional.empty()
class NoOpReverseGeocodingAdapterTest {

    @Test
    void reverse_alwaysReturnsEmpty() {
        NoOpReverseGeocodingAdapter adapter = new NoOpReverseGeocodingAdapter();

        assertThat(adapter.reverse(0.0, 0.0)).isEmpty();
        assertThat(adapter.reverse(40.481, -3.364)).isEmpty();
        assertThat(adapter.reverse(-90.0, 180.0)).isEmpty();
    }
}
