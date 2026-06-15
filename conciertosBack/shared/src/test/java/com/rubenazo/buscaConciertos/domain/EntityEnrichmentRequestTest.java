package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntityEnrichmentRequestTest {

    @Test
    void shouldConstructRequestWithAllFields() {
        TavilyResult evidence1 = new TavilyResult("Title", "https://example.com", "Content", 0.9);

        EntityEnrichmentRequest request = new EntityEnrichmentRequest(
            "sala",
            "sala-123",
            "Sala Apolo",
            "Barcelona",
            "Cataluña",
            Map.of("address", "Carrer Nou de la Rambla, 113"),
            List.of("description"),
            Map.of(),
            Map.of("description", List.of(evidence1))
        );

        assertThat(request.entityType()).isEqualTo("sala");
        assertThat(request.entityId()).isEqualTo("sala-123");
        assertThat(request.name()).isEqualTo("Sala Apolo");
        assertThat(request.city()).isEqualTo("Barcelona");
        assertThat(request.province()).isEqualTo("Cataluña");
        assertThat(request.knownFields()).containsKey("address");
        assertThat(request.missingFields()).containsExactly("description");
        assertThat(request.contextHints()).isEmpty();
        assertThat(request.evidence()).containsKey("description");
        assertThat(request.evidence().get("description")).hasSize(1);
    }

    @Test
    void shouldAllowNullCityAndProvinceForArtists() {
        EntityEnrichmentRequest request = new EntityEnrichmentRequest(
            "artist",
            "artist-456",
            "Vetusta Morla",
            null,
            null,
            Map.of("genre", "indie rock"),
            List.of("description"),
            Map.of(),
            Map.of()
        );

        assertThat(request.city()).isNull();
        assertThat(request.province()).isNull();
        assertThat(request.entityType()).isEqualTo("artist");
    }

    @Test
    void shouldSupportContextHintsForArtistCityGrounding() {
        EntityEnrichmentRequest request = new EntityEnrichmentRequest(
            "artist",
            "artist-789",
            "La Oreja de Van Gogh",
            null,
            null,
            Map.of(),
            List.of("genre"),
            Map.of("venues_cities", "Madrid, Barcelona"),
            Map.of()
        );

        assertThat(request.contextHints()).containsEntry("venues_cities", "Madrid, Barcelona");
    }

    @Test
    void shouldAllowEmptyEvidenceMap() {
        EntityEnrichmentRequest request = new EntityEnrichmentRequest(
            "sala",
            "sala-001",
            "Sala Razzmatazz",
            "Barcelona",
            "Cataluña",
            Map.of(),
            List.of("description"),
            Map.of(),
            Map.of()
        );

        assertThat(request.evidence()).isEmpty();
    }

    @Test
    void shouldSupportEnrichedEntityResultEmpty() {
        EnrichedEntityResult result = EnrichedEntityResult.empty();

        assertThat(result.fields()).isEmpty();
    }

    @Test
    void shouldConstructEnrichedFieldValue() {
        EnrichedFieldValue fieldValue = new EnrichedFieldValue(
            "Sala de conciertos en el centro de Barcelona",
            "https://sala-apolo.com",
            0.92
        );

        assertThat(fieldValue.value()).isEqualTo("Sala de conciertos en el centro de Barcelona");
        assertThat(fieldValue.sourceUrl()).isEqualTo("https://sala-apolo.com");
        assertThat(fieldValue.confidence()).isEqualTo(0.92);
    }

    @Test
    void shouldAllowNullValueInEnrichedFieldValue() {
        EnrichedFieldValue fieldValue = new EnrichedFieldValue(null, null, 0.0);

        assertThat(fieldValue.value()).isNull();
        assertThat(fieldValue.sourceUrl()).isNull();
        assertThat(fieldValue.confidence()).isEqualTo(0.0);
    }
}
