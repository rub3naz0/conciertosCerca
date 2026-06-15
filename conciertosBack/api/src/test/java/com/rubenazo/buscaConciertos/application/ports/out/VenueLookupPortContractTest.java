package com.rubenazo.buscaConciertos.application.ports.out;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compile-time contract test: verifies VenueLookupPort and VenueMatch shape.
 */
class VenueLookupPortContractTest {

    @Test
    void venueLookupPort_hasExpectedSignature() {
        // Verify the interface exists and has correct method
        VenueLookupPort port = (name, city, province) -> Optional.empty();
        Optional<VenueMatch> result = port.lookup("Sala Apolo", "Barcelona", "Cataluña");
        assertThat(result).isEmpty();
    }

    @Test
    void venueMatch_hasAllRequiredFields() {
        VenueMatch match = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.95, "name", "foursquare");
        assertThat(match.lat()).isEqualTo(41.375);
        assertThat(match.lng()).isEqualTo(2.169);
        assertThat(match.displayName()).isEqualTo("Sala Apolo");
        assertThat(match.confidence()).isEqualTo(0.95);
        assertThat(match.matchType()).isEqualTo("name");
        assertThat(match.provider()).isEqualTo("foursquare");
    }

    @Test
    void venueMatch_packageIsApplicationPortsOut() {
        // Verify package declaration
        assertThat(VenueMatch.class.getPackageName())
            .isEqualTo("com.rubenazo.buscaConciertos.application.ports.out");
    }

    @Test
    void venueLookupPort_packageIsApplicationPortsOut() {
        assertThat(VenueLookupPort.class.getPackageName())
            .isEqualTo("com.rubenazo.buscaConciertos.application.ports.out");
    }
}
