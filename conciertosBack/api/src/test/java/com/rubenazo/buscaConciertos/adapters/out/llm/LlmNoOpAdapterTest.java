package com.rubenazo.buscaConciertos.adapters.out.llm;

import com.rubenazo.buscaConciertos.domain.EntityEnrichmentRequest;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LlmNoOpAdapterTest {

    private final LlmNoOpAdapter adapter = new LlmNoOpAdapter();

    @Test
    void enrich_returnsEmptyResultWithoutException() {
        EntityEnrichmentRequest request = new EntityEnrichmentRequest(
            "sala", "sala-1", "Sala Apolo", "Barcelona", "Cataluña",
            Map.of("name", "Sala Apolo"),
            List.of("address", "description"),
            Map.of(),
            Map.of()
        );

        EnrichedEntityResult result = adapter.enrich(request);

        assertThat(result).isNotNull();
        assertThat(result.fields()).isEmpty();
    }

    @Test
    void enrich_doesNotThrow() {
        EntityEnrichmentRequest request = new EntityEnrichmentRequest(
            "artist", "artist-1", "Vetusta Morla", null, null,
            Map.of("name", "Vetusta Morla"),
            List.of("genre"),
            Map.of(),
            Map.of()
        );

        assertThatCode(() -> adapter.enrich(request)).doesNotThrowAnyException();
    }
}
