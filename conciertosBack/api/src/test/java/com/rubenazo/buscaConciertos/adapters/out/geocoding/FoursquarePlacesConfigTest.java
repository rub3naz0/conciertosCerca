package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the FoursquarePlacesConfig bean wiring logic.
 * Tests the key-blank → NoOp and key-present → FoursquarePlacesAdapter paths.
 */
class FoursquarePlacesConfigTest {

    private final FoursquarePlacesConfig config = new FoursquarePlacesConfig();

    @Test
    void whenApiKeyIsBlank_registersNoOpAdapter() {
        VenueLookupPort port = config.venueLookupPort("", "https://places-api.foursquare.com", "2025-06-17");
        assertThat(port).isInstanceOf(NoOpVenueLookupAdapter.class);
        assertThat(port.lookup("any", "any", "any")).isEmpty();
    }

    @Test
    void whenApiKeyIsNull_registersNoOpAdapter() {
        VenueLookupPort port = config.venueLookupPort(null, "https://places-api.foursquare.com", "2025-06-17");
        assertThat(port).isInstanceOf(NoOpVenueLookupAdapter.class);
        assertThat(port.lookup("any", "any", "any")).isEmpty();
    }

    @Test
    void whenApiKeyIsPresent_registersFoursquarePlacesAdapter() {
        VenueLookupPort port = config.venueLookupPort("my-real-api-key", "https://places-api.foursquare.com", "2025-06-17");
        assertThat(port).isInstanceOf(FoursquarePlacesAdapter.class);
    }
}
