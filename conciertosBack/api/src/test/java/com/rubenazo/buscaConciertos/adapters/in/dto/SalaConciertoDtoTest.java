package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SalaConciertoDtoTest {

    @Test
    void fromShouldMapAllFieldsFromDomain() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla 113",
                "Barcelona", "Barcelona", 41.3735, 2.1700,
                "http://img.jpg", "Historic venue in Barcelona", null, Instant.parse("2026-05-20T10:00:00Z"));

        SalaConciertoDto dto = SalaConciertoDto.from(sala);

        assertThat(dto.id()).isEqualTo("s1");
        assertThat(dto.name()).isEqualTo("Sala Apolo");
        assertThat(dto.address()).isEqualTo("C/ Nou de la Rambla 113");
        assertThat(dto.city()).isEqualTo("Barcelona");
        assertThat(dto.province()).isEqualTo("Barcelona");
        assertThat(dto.lat()).isEqualTo(41.3735);
        assertThat(dto.lng()).isEqualTo(2.1700);
        assertThat(dto.imageUrl()).isEqualTo("http://img.jpg");
        assertThat(dto.description()).isEqualTo("Historic venue in Barcelona");
    }

    @Test
    void fromShouldNotIncludePhoneOrWebsite() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla 113",
                "Barcelona", "Barcelona", 41.3735, 2.1700,
                "http://img.jpg", null, null, Instant.parse("2026-05-20T10:00:00Z"));

        SalaConciertoDto dto = SalaConciertoDto.from(sala);

        assertThat(dto.getClass().getRecordComponents())
                .extracting("name")
                .doesNotContain("phone", "website");
    }

    @Test
    void fromShouldHandleNullCoordinates() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla 113",
                "Barcelona", "Barcelona", null, null, null, null, null, Instant.now());

        SalaConciertoDto dto = SalaConciertoDto.from(sala);

        assertThat(dto.lat()).isNull();
        assertThat(dto.lng()).isNull();
    }

    @Test
    void fromShouldNotIncludeUpdatedAt() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla 113",
                "Barcelona", "Barcelona", 41.3735, 2.1700,
                "http://img.jpg", null, null, Instant.parse("2026-05-20T10:00:00Z"));

        SalaConciertoDto dto = SalaConciertoDto.from(sala);

        assertThat(dto.getClass().getRecordComponents())
                .extracting("name")
                .doesNotContain("updatedAt");
    }
}
