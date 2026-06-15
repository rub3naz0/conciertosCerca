package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.GeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.ValidatedGeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupCandidate;
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupPort;
import com.rubenazo.buscaConciertos.application.ports.out.VenueMatch;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenueGeocodingUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-04T10:00:00Z");
    private static final double THRESHOLD = 0.8;

    @Mock
    private VenueLookupPort venueLookupPort;

    @Mock
    private GeocodingPort geocodingPort;

    private VenueGeocodingUseCase useCase;

    private static final SalaConcierto SALA_WITH_COORDS = new SalaConcierto(
        "s1", "Sala Test", "Calle Mayor 5", "Madrid", "Madrid",
        40.0, -3.0, null, null, null, NOW);

    private static final SalaConcierto SALA_NO_COORDS = new SalaConcierto(
        "s1", "Sala Apolo", "Carrer Nou 113", "Barcelona", "Cataluña",
        null, null, null, null, null, NOW);

    @BeforeEach
    void setUp() {
        useCase = new VenueGeocodingUseCase(venueLookupPort, geocodingPort, THRESHOLD);
    }

    // --- Foursquare above threshold — accepted immediately ---

    @Test
    void geocode_foursquareAboveThreshold_returnsPrimaryAndSkipsLocationIQ() {
        VenueMatch primary = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.92, "name", "foursquare");
        when(venueLookupPort.lookup("Sala Apolo", "Barcelona", "Cataluña"))
            .thenReturn(Optional.of(primary));

        Optional<VenueMatch> result = useCase.geocode(SALA_NO_COORDS);

        assertThat(result).isPresent();
        assertThat(result.get().provider()).isEqualTo("foursquare");
        assertThat(result.get().confidence()).isEqualTo(0.92);
        verify(geocodingPort, never()).geocode(anyString(), anyString(), anyString());
    }

    // --- Foursquare below threshold — fallback to LocationIQ ---

    // DESIGN DECISION (Judgment Day 2026-06-08): when Foursquare is below threshold and
    // LocationIQ returns a (centroid-rejected) result, the LocationIQ address geocode WINS,
    // even if its numeric confidence is lower than Foursquare's. This is intentional, NOT a
    // ranking bug: the two confidences are not comparable (Foursquare = name similarity;
    // LocationIQ = Nominatim importance, which is map prominence, not match confidence), and
    // a validated address geocode is generally more precise than a weak POI name match.
    // It does not affect written data either — geocodeIfNeeded only auto-writes at >= threshold,
    // so sub-threshold results only surface as manual-review candidates. Do NOT "fix" this to
    // prefer the higher confidence without revisiting that semantics.
    @Test
    void geocode_foursquareBelowThreshold_fallsBackToLocationIQ() {
        VenueMatch weakPrimary = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.65, "name", "foursquare");
        when(venueLookupPort.lookup("Sala Apolo", "Barcelona", "Cataluña"))
            .thenReturn(Optional.of(weakPrimary));
        // LocationIQ returns valid coords
        when(geocodingPort.geocode("Carrer Nou 113", "Barcelona", "Cataluña"))
            .thenReturn(Optional.of(new GeocodingPort.Coordinates(41.376, 2.170)));

        Optional<VenueMatch> result = useCase.geocode(SALA_NO_COORDS);

        assertThat(result).isPresent();
        assertThat(result.get().provider()).isEqualTo("locationiq");
        assertThat(result.get().matchType()).isEqualTo("address");
    }

    // --- Foursquare empty — fallback to LocationIQ ---

    @Test
    void geocode_foursquareEmpty_fallsBackToLocationIQ() {
        when(venueLookupPort.lookup(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(geocodingPort.geocode("Carrer Nou 113", "Barcelona", "Cataluña"))
            .thenReturn(Optional.of(new GeocodingPort.Coordinates(41.376, 2.170)));

        Optional<VenueMatch> result = useCase.geocode(SALA_NO_COORDS);

        assertThat(result).isPresent();
        assertThat(result.get().provider()).isEqualTo("locationiq");
    }

    // --- Both providers return empty ---

    @Test
    void geocode_bothProvidersEmpty_returnsEmpty() {
        when(venueLookupPort.lookup(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(geocodingPort.geocode(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        Optional<VenueMatch> result = useCase.geocode(SALA_NO_COORDS);

        assertThat(result).isEmpty();
    }

    // --- Sub-threshold primary returned when LocationIQ also empty ---

    @Test
    void geocode_weakFoursquare_locationIqEmpty_returnsPrimary() {
        VenueMatch weakPrimary = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.65, "name", "foursquare");
        when(venueLookupPort.lookup(anyString(), anyString(), anyString()))
            .thenReturn(Optional.of(weakPrimary));
        when(geocodingPort.geocode(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        Optional<VenueMatch> result = useCase.geocode(SALA_NO_COORDS);

        // Sub-threshold primary returned for DataQuality manual routing
        assertThat(result).isPresent();
        assertThat(result.get().provider()).isEqualTo("foursquare");
        assertThat(result.get().confidence()).isEqualTo(0.65);
    }

    // --- LocationIQ returns NaN importance — confidence must not propagate NaN ---

    @Test
    void geocode_locationIqNaNImportance_treatedAsZeroConfidence() {
        ValidatedGeocodingPort validated = mock(ValidatedGeocodingPort.class);
        VenueGeocodingUseCase uc = new VenueGeocodingUseCase(venueLookupPort, validated, THRESHOLD);
        when(venueLookupPort.lookup(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(validated.geocodeValidated("Carrer Nou 113", "Barcelona", "Cataluña"))
            .thenReturn(Optional.of(new ValidatedGeocodingPort.ValidatedCoordinates(41.376, 2.170, Double.NaN)));

        Optional<VenueMatch> result = uc.geocode(SALA_NO_COORDS);

        assertThat(result).isPresent();
        assertThat(Double.isNaN(result.get().confidence())).isFalse();
        assertThat(result.get().confidence()).isEqualTo(0.0);
    }

    // --- Foursquare-only candidate lookup for fill-missing ---

    @Test
    void bestFoursquareCandidate_usesCandidateLookupAndSkipsLocationIQ() {
        VenueLookupCandidate weak = new VenueLookupCandidate(41.0, 2.0, "Apolo Store",
            "Barcelona", 0.45, "name", "foursquare");
        VenueLookupCandidate strong = new VenueLookupCandidate(41.375, 2.169, "Sala Apolo",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(venueLookupPort.lookupCandidates("Sala Apolo", "Barcelona", "Cataluña", 3))
            .thenReturn(List.of(weak, strong));

        Optional<VenueLookupCandidate> result = useCase.bestFoursquareCandidate(SALA_NO_COORDS, 3);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(strong);
        verify(geocodingPort, never()).geocode(anyString(), anyString(), anyString());
    }

    // --- Idempotency: sala with existing coords is skipped ---

    @Test
    void geocodeIfNeeded_skipsWhenCoordsAlreadySet() {
        SalaConcierto result = useCase.geocodeIfNeeded(SALA_WITH_COORDS, NOW);

        assertThat(result).isSameAs(SALA_WITH_COORDS);
        verify(venueLookupPort, never()).lookup(anyString(), anyString(), anyString());
        verify(geocodingPort, never()).geocode(anyString(), anyString(), anyString());
    }

    // --- Both NoOp: no exception, lat/lng remain null ---

    @Test
    void geocodeIfNeeded_bothNoOp_noCoordsNoException() {
        when(venueLookupPort.lookup(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(geocodingPort.geocode(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        SalaConcierto result = useCase.geocodeIfNeeded(SALA_NO_COORDS, NOW);

        assertThat(result.lat()).isNull();
        assertThat(result.lng()).isNull();
    }

    // --- geocodeIfNeeded: auto-writes ONLY when confidence >= threshold ---

    @Test
    void geocodeIfNeeded_aboveThreshold_writesCoords() {
        VenueMatch strongMatch = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.92, "name", "foursquare");
        when(venueLookupPort.lookup(anyString(), anyString(), anyString()))
            .thenReturn(Optional.of(strongMatch));

        SalaConcierto result = useCase.geocodeIfNeeded(SALA_NO_COORDS, NOW);

        assertThat(result.lat()).isEqualTo(41.375);
        assertThat(result.lng()).isEqualTo(2.169);
    }

    @Test
    void geocodeIfNeeded_belowThreshold_doesNotWriteCoords() {
        VenueMatch weakMatch = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.65, "name", "foursquare");
        when(venueLookupPort.lookup(anyString(), anyString(), anyString()))
            .thenReturn(Optional.of(weakMatch));
        when(geocodingPort.geocode(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        SalaConcierto result = useCase.geocodeIfNeeded(SALA_NO_COORDS, NOW);

        assertThat(result.lat()).isNull();
        assertThat(result.lng()).isNull();
    }
}
