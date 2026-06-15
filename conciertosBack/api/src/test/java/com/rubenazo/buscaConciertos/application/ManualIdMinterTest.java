package com.rubenazo.buscaConciertos.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ManualIdMinterTest {

    @Test
    void salaId_startsWithManualSalaPrefix() {
        String id = ManualIdMinter.salaId("Sala Roca", "Madrid");
        assertThat(id).startsWith("manual-sala-");
    }

    @Test
    void salaId_containsProvinceSlug() {
        String id = ManualIdMinter.salaId("Sala Roca", "Madrid");
        assertThat(id).contains("madrid");
    }

    @Test
    void artistId_startsWithManualArtistPrefix() {
        String id = ManualIdMinter.artistId("Artista X");
        assertThat(id).startsWith("manual-artist-");
    }

    @Test
    void artistId_isDeterministic() {
        String id1 = ManualIdMinter.artistId("Artista X");
        String id2 = ManualIdMinter.artistId("Artista X");
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void concertId_startsWithManualPrefix() {
        String salaId = ManualIdMinter.salaId("Sala Test", "Valencia");
        String id = ManualIdMinter.concertId(salaId, LocalDate.of(2026, 8, 1));
        assertThat(id).startsWith("manual-");
    }

    @Test
    void concertId_isDeterministic() {
        String salaId = ManualIdMinter.salaId("Sala Test", "Valencia");
        String id1 = ManualIdMinter.concertId(salaId, LocalDate.of(2026, 8, 1));
        String id2 = ManualIdMinter.concertId(salaId, LocalDate.of(2026, 8, 1));
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void salaId_noWhitespace() {
        String id = ManualIdMinter.salaId("Sala Roca", "Madrid");
        assertThat(id).doesNotContainAnyWhitespaces();
    }

    @Test
    void salaId_noUppercase() {
        String id = ManualIdMinter.salaId("Sala Roca", "Madrid");
        assertThat(id).isEqualTo(id.toLowerCase());
    }

    @Test
    void artistId_noWhitespaceOrUppercase() {
        String id = ManualIdMinter.artistId("Los Grandes del Rock");
        assertThat(id).doesNotContainAnyWhitespaces();
        assertThat(id).isEqualTo(id.toLowerCase());
    }

    @Test
    void concertId_noWhitespaceOrUppercase() {
        String salaId = ManualIdMinter.salaId("Sala Tests", "Barcelona");
        String id = ManualIdMinter.concertId(salaId, LocalDate.of(2026, 9, 15));
        assertThat(id).doesNotContainAnyWhitespaces();
        assertThat(id).isEqualTo(id.toLowerCase());
    }
}
