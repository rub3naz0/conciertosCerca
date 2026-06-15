package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SalaConciertoTest {

    @Test
    void shouldCreateSalaConciertoWithAllFields() {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla 113",
                "Barcelona", "Barcelona", 41.3735, 2.1700, "http://img.jpg",
                "Una sala de conciertos en Barcelona", "http://source", now);

        assertThat(sala.id()).isEqualTo("s1");
        assertThat(sala.name()).isEqualTo("Sala Apolo");
        assertThat(sala.address()).isEqualTo("C/ Nou de la Rambla 113");
        assertThat(sala.city()).isEqualTo("Barcelona");
        assertThat(sala.province()).isEqualTo("Barcelona");
        assertThat(sala.lat()).isEqualTo(41.3735);
        assertThat(sala.lng()).isEqualTo(2.1700);
        assertThat(sala.imageUrl()).isEqualTo("http://img.jpg");
        assertThat(sala.description()).isEqualTo("Una sala de conciertos en Barcelona");
        assertThat(sala.sourceUrl()).isEqualTo("http://source");
        assertThat(sala.updatedAt()).isEqualTo(now);
    }

    @Test
    void shouldNotHavePhoneOrWebsite() {
        // SalaConcierto must NOT have phone or website fields — constructor
        // accepts exactly 11 parameters (id, name, address, city, province,
        // lat, lng, imageUrl, description, sourceUrl, updatedAt).
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla 113",
                "Barcelona", "Barcelona", 41.3735, 2.1700, "http://img.jpg",
                "Descripción", "http://source", Instant.now());

        assertThat(sala.imageUrl()).isEqualTo("http://img.jpg");
        assertThat(sala.description()).isEqualTo("Descripción");
        assertThat(sala.sourceUrl()).isEqualTo("http://source");
    }

    @Test
    void shouldSupportNullOptionalFields() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla 113",
                "Barcelona", "Barcelona", null, null, null, null, null, Instant.now());

        assertThat(sala.lat()).isNull();
        assertThat(sala.lng()).isNull();
        assertThat(sala.imageUrl()).isNull();
        assertThat(sala.description()).isNull();
        assertThat(sala.sourceUrl()).isNull();
    }
}
