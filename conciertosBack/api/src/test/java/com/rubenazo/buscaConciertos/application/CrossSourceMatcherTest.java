package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CrossSourceMatcherTest {

    private static final Instant NOW = Instant.parse("2026-06-05T10:00:00Z");

    private final CrossSourceMatcher matcher = new CrossSourceMatcher();

    @Test
    void venueWithin150MetersMatchesExistingVenueId() {
        SalaConcierto existing = venue("madrid-sala-riviera", "Sala Riviera", 40.40480, -3.70690);
        SalaConcierto aem = venue("aem-venue-42", "Sala Riviera", 40.40510, -3.70680);

        CrossSourceMatcher.VenueResolution resolution = matcher.resolveVenues(List.of(aem), List.of(existing));

        assertThat(resolution.resolvedIdFor("aem-venue-42")).isEqualTo("madrid-sala-riviera");
        assertThat(resolution.matched("aem-venue-42")).isTrue();
    }

    @Test
    void venueOutside150MetersKeepsAemVenueId() {
        SalaConcierto existing = venue("madrid-sala-riviera", "Sala Riviera", 40.40480, -3.70690);
        SalaConcierto aem = venue("aem-venue-42", "Sala Riviera", 40.41000, -3.70680);

        CrossSourceMatcher.VenueResolution resolution = matcher.resolveVenues(List.of(aem), List.of(existing));

        assertThat(resolution.resolvedIdFor("aem-venue-42")).isEqualTo("aem-venue-42");
        assertThat(resolution.matched("aem-venue-42")).isFalse();
    }

    @Test
    void venueSlugFallbackMatchesWhenExistingCoordsAreNull() {
        SalaConcierto existing = venue("madrid-sala-riviera", "Sala Riviera", null, null);
        SalaConcierto aem = venue("aem-venue-42", "Sala Riviera", 40.40510, -3.70680);

        CrossSourceMatcher.VenueResolution resolution = matcher.resolveVenues(List.of(aem), List.of(existing));

        assertThat(resolution.resolvedIdFor("aem-venue-42")).isEqualTo("madrid-sala-riviera");
        assertThat(resolution.matched("aem-venue-42")).isTrue();
    }

    @Test
    void ambiguousVenueCoordinateMatchesKeepAemVenueId() {
        SalaConcierto existingOne = venue("madrid-sala-riviera", "Sala Riviera", 40.40480, -3.70690);
        SalaConcierto existingTwo = venue("madrid-sala-neighbor", "Sala Neighbor", 40.40500, -3.70670);
        SalaConcierto aem = venue("aem-venue-42", "Sala Riviera", 40.40490, -3.70680);

        CrossSourceMatcher.VenueResolution resolution = matcher.resolveVenues(List.of(aem), List.of(existingOne, existingTwo));

        assertThat(resolution.resolvedIdFor("aem-venue-42")).isEqualTo("aem-venue-42");
        assertThat(resolution.matched("aem-venue-42")).isFalse();
    }

    @Test
    void artistSlugMatchReusesExistingArtistId() {
        Artist aem = artist("aem-band-101", "Vetusta Morla");

        CrossSourceMatcher.ArtistResolution resolution = matcher.resolveArtists(List.of(aem), Set.of("vetusta-morla"));

        assertThat(resolution.resolvedIdFor("aem-band-101")).isEqualTo("vetusta-morla");
        assertThat(resolution.matched("aem-band-101")).isTrue();
    }

    @Test
    void artistWithoutSlugMatchKeepsAemBandId() {
        Artist aem = artist("aem-band-101", "Vetusta Morla");

        CrossSourceMatcher.ArtistResolution resolution = matcher.resolveArtists(List.of(aem), Set.of("los-planetas"));

        assertThat(resolution.resolvedIdFor("aem-band-101")).isEqualTo("aem-band-101");
        assertThat(resolution.matched("aem-band-101")).isFalse();
    }

    @Test
    void concertUniqueVenueAndDateMatchReusesExistingConcertId() {
        Concert aem = concert("aem-event-abc", "aem-venue-42", LocalDate.of(2026, 7, 15), List.of("aem-band-101"));
        Concert existing = concert("concert-existing", "madrid-sala-riviera", LocalDate.of(2026, 7, 15), List.of("vetusta-morla"));

        CrossSourceMatcher.ConcertResolution resolution = matcher.resolveConcerts(
            List.of(aem),
            Map.of("aem-venue-42", "madrid-sala-riviera"),
            List.of(existing)
        );

        assertThat(resolution.resolvedIdFor("aem-event-abc")).isEqualTo("concert-existing");
        assertThat(resolution.matched("aem-event-abc")).isTrue();
    }

    @Test
    void ambiguousConcertVenueAndDateMatchKeepsAemEventId() {
        Concert aem = concert("aem-event-abc", "aem-venue-42", LocalDate.of(2026, 7, 15), List.of("aem-band-101"));
        Concert existingOne = concert("concert-existing-1", "madrid-sala-riviera", LocalDate.of(2026, 7, 15), List.of("artist-1"));
        Concert existingTwo = concert("concert-existing-2", "madrid-sala-riviera", LocalDate.of(2026, 7, 15), List.of("artist-2"));

        CrossSourceMatcher.ConcertResolution resolution = matcher.resolveConcerts(
            List.of(aem),
            Map.of("aem-venue-42", "madrid-sala-riviera"),
            List.of(existingOne, existingTwo)
        );

        assertThat(resolution.resolvedIdFor("aem-event-abc")).isEqualTo("aem-event-abc");
        assertThat(resolution.matched("aem-event-abc")).isFalse();
    }

    @Test
    void concertWithNullVenueDoesNotThrowAndKeepsAemEventId() {
        Concert aem = concert("aem-event-no-venue", null, LocalDate.of(2026, 7, 15), List.of("aem-band-101"));

        CrossSourceMatcher.ConcertResolution resolution = matcher.resolveConcerts(
            List.of(aem),
            Map.of("aem-venue-42", "madrid-sala-riviera"),
            List.of(concert("concert-existing", "madrid-sala-riviera", LocalDate.of(2026, 7, 15), List.of("artist-1")))
        );

        assertThat(resolution.resolvedIdFor("aem-event-no-venue")).isEqualTo("aem-event-no-venue");
        assertThat(resolution.matched("aem-event-no-venue")).isFalse();
    }

    // Phase C3.1 — title-derived artist slug matches existing artist id → reuse
    // CrossSourceMatcher.resolveArtists uses additiveId(aemArtist.id(), ...) as the map key.
    // For id="aem-artist-vetusta-morla" (non-blank), additiveId returns it unchanged.
    // Slug matching then checks slugify(name)="vetusta-morla" against existingArtistIds.
    // This already works for arbitrary "aem-*" prefixes without code change (D13).
    @Test
    void titleDerivedArtistSlugMatchReusesExistingArtistId() {
        Artist aem = artist("aem-artist-vetusta-morla", "VETUSTA MORLA");

        CrossSourceMatcher.ArtistResolution resolution = matcher.resolveArtists(
            List.of(aem), Set.of("vetusta-morla"));

        assertThat(resolution.resolvedIdFor("aem-artist-vetusta-morla")).isEqualTo("vetusta-morla");
        assertThat(resolution.matched("aem-artist-vetusta-morla")).isTrue();
    }

    // Phase C3.2 — title-derived artist with no slug match keeps its aem-artist-{slug} id
    // "quartet-tarantino" != "quartet-tarantino-garage-moon" — exact match only (D13 conservative).
    @Test
    void titleDerivedArtistNoSlugMatchKeepsAemArtistId() {
        Artist aem = artist("aem-artist-quartet-tarantino", "QUARTET TARANTINO");

        CrossSourceMatcher.ArtistResolution resolution = matcher.resolveArtists(
            List.of(aem), Set.of("quartet-tarantino-garage-moon"));

        assertThat(resolution.resolvedIdFor("aem-artist-quartet-tarantino")).isEqualTo("aem-artist-quartet-tarantino");
        assertThat(resolution.matched("aem-artist-quartet-tarantino")).isFalse();
    }

    @Test
    void existingFixtureIdsDoNotUseAemPrefix() {
        Set<String> existingVenueIds = Set.of("madrid-sala-riviera", "guadalajara-tyce");
        Set<String> existingArtistIds = Set.of("vetusta-morla", "los-planetas");
        Set<String> existingConcertIds = Set.of("madrid-sala-riviera-vetusta-morla-2026-07-15");

        assertThat(existingVenueIds).noneMatch(id -> id.startsWith("aem-"));
        assertThat(existingArtistIds).noneMatch(id -> id.startsWith("aem-"));
        assertThat(existingConcertIds).noneMatch(id -> id.startsWith("aem-"));
    }

    // --- Edge-case coverage (Judgment Day 2026-06-08) ---

    // slugify() strips diacritics, so an accented AEM venue name still matches an
    // unaccented existing venue via the slug fallback (existing has null coords).
    @Test
    void accentedVenueNameMatchesViaSlugFallback() {
        SalaConcierto existing = venue("madrid-sala-apolo", "Sala Apolo", null, null);
        SalaConcierto aem = venue("aem-venue-77", "Sàlà Apóló", 40.40510, -3.70680);

        CrossSourceMatcher.VenueResolution resolution = matcher.resolveVenues(List.of(aem), List.of(existing));

        assertThat(resolution.resolvedIdFor("aem-venue-77")).isEqualTo("madrid-sala-apolo");
        assertThat(resolution.matched("aem-venue-77")).isTrue();
    }

    // CONSERVATIVE BY DESIGN: when BOTH venues have coordinates but are >150m apart,
    // the slug fallback (null-coords only) never runs, so a same-named venue with
    // drifted coordinates is treated as new. Documents the precision-over-recall choice.
    @Test
    void venueWithCoordsBeyond150mDoesNotFallBackToSlugEvenWithSameName() {
        SalaConcierto existing = venue("madrid-sala-riviera", "Sala Riviera", 40.40480, -3.70690);
        SalaConcierto aem = venue("aem-venue-42", "Sala Riviera", 40.42000, -3.70680);

        CrossSourceMatcher.VenueResolution resolution = matcher.resolveVenues(List.of(aem), List.of(existing));

        assertThat(resolution.resolvedIdFor("aem-venue-42")).isEqualTo("aem-venue-42");
        assertThat(resolution.matched("aem-venue-42")).isFalse();
    }

    // The slug fallback also matches a province-prefixed existing id by suffix
    // ({province}-{venueSlug} ends with "-"+aemSlug), even when the existing display
    // name slug differs. Documents the endsWith path in existingIdNamePartMatches.
    @Test
    void slugFallbackMatchesProvincePrefixedVenueIdBySuffix() {
        SalaConcierto existing = venue("madrid-sala-riviera", "La Riviera", null, null);
        SalaConcierto aem = venue("aem-venue-42", "Sala Riviera", 40.40510, -3.70680);

        CrossSourceMatcher.VenueResolution resolution = matcher.resolveVenues(List.of(aem), List.of(existing));

        assertThat(resolution.resolvedIdFor("aem-venue-42")).isEqualTo("madrid-sala-riviera");
        assertThat(resolution.matched("aem-venue-42")).isTrue();
    }

    // CONSERVATIVE BY DESIGN: concert matching keys on venue+date only; artist lineup
    // is NOT consulted. A single existing concert at the same venue/date matches even
    // when the artists differ. Documents the venue+date equality contract.
    @Test
    void concertMatchesOnVenueAndDateEvenWhenArtistsDiffer() {
        Concert aem = concert("aem-event-abc", "aem-venue-42", LocalDate.of(2026, 7, 15), List.of("aem-band-101"));
        Concert existing = concert("concert-existing", "madrid-sala-riviera", LocalDate.of(2026, 7, 15), List.of("totally-different-artist"));

        CrossSourceMatcher.ConcertResolution resolution = matcher.resolveConcerts(
            List.of(aem),
            Map.of("aem-venue-42", "madrid-sala-riviera"),
            List.of(existing)
        );

        assertThat(resolution.resolvedIdFor("aem-event-abc")).isEqualTo("concert-existing");
        assertThat(resolution.matched("aem-event-abc")).isTrue();
    }

    @Test
    void accentedArtistNameMatchesExistingSlug() {
        Artist aem = artist("aem-band-101", "Vetusta Morlá");

        CrossSourceMatcher.ArtistResolution resolution = matcher.resolveArtists(List.of(aem), Set.of("vetusta-morla"));

        assertThat(resolution.resolvedIdFor("aem-band-101")).isEqualTo("vetusta-morla");
        assertThat(resolution.matched("aem-band-101")).isTrue();
    }

    @Test
    void emptyInputsProduceEmptyResolutions() {
        assertThat(matcher.resolveVenues(List.of(), List.of()).assignments()).isEmpty();
        assertThat(matcher.resolveArtists(List.of(), Set.of()).assignments()).isEmpty();
        assertThat(matcher.resolveConcerts(List.of(), Map.of(), List.of()).assignments()).isEmpty();
    }

    @Test
    void nullInputsDoNotThrowAndProduceEmptyResolutions() {
        assertThat(matcher.resolveVenues(null, null).assignments()).isEmpty();
        assertThat(matcher.resolveArtists(null, null).assignments()).isEmpty();
        assertThat(matcher.resolveConcerts(null, null, null).assignments()).isEmpty();
    }

    private static SalaConcierto venue(String id, String name, Double lat, Double lng) {
        return new SalaConcierto(id, name, null, null, null, lat, lng, null, null, null, NOW);
    }

    private static Artist artist(String id, String name) {
        return new Artist(id, name, null, null, null, null, null, NOW);
    }

    private static Concert concert(String id, String venueId, LocalDate date, List<String> artistIds) {
        return new Concert(id, venueId, artistIds, date, "21:00", "15€", "https://example.test/" + id, NOW);
    }
}
