package com.rubenazo.buscaConciertos.adapters.out.alcala;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AlcalaApiClientAdapterTest {

    private WireMockServer wireMock;
    private AlcalaApiClientAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:" + wireMock.port())
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
        adapter = new AlcalaApiClientAdapter(restClient);

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_venues/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/bands/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // Phase 2.1 — pagination termination: two pages, meta.next null on second
    @Test
    void fetch_paginatesUntilMetaNextIsNull() {
        String page1Body = """
            {
              "meta": { "next": "http://localhost:%d/api/v1/upcoming_events/?page=2" },
              "events": [
                {
                  "event_uid": "evt-1",
                  "day": "2026-07-10",
                  "time": "21:00",
                  "price": "15.00",
                  "price_preorder": null,
                  "ticket_link": "https://example.com/t1",
                  "bands": [
                    { "id": 10, "name": "Band One", "slug": "band-one" }
                  ],
                  "venues": {
                    "id": 1,
                    "name": "Sala Test",
                    "latitude": "40.481",
                    "longitude": "-3.364",
                    "image": "https://example.com/sala.jpg"
                  }
                }
              ]
            }
            """.formatted(wireMock.port());

        String page2Body = """
            {
              "meta": { "next": null },
              "events": [
                {
                  "event_uid": "evt-2",
                  "day": "2026-07-11",
                  "time": "22:00",
                  "price": "12.00",
                  "price_preorder": null,
                  "ticket_link": "https://example.com/t2",
                  "bands": [
                    { "id": 20, "name": "Band Two", "slug": "band-two" }
                  ],
                  "venues": {
                    "id": 2,
                    "name": "Sala Dos",
                    "latitude": "40.490",
                    "longitude": "-3.370",
                    "image": null
                  }
                }
              ]
            }
            """;

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(page1Body)));

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .withQueryParam("page", com.github.tomakehurst.wiremock.client.WireMock.equalTo("2"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(page2Body)));

        AlcalaSnapshot snapshot = adapter.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/upcoming_events/")));
        assertThat(snapshot.concerts()).hasSize(2);
        assertThat(snapshot.concerts()).extracting(c -> c.id())
            .containsExactlyInAnyOrder("aem-event-evt-1", "aem-event-evt-2");
    }

    // Phase 2.2 — hard page cap: meta.next always non-null, must stop at MAX_PAGES
    @Test
    void fetch_stopsAtMaxPagesAndDoesNotThrow() {
        // Always returns meta.next pointing to next page — infinite loop candidate
        String pageBody = """
            {
              "meta": { "next": "http://localhost:%d/api/v1/upcoming_events/?page=999" },
              "events": [
                {
                  "event_uid": "evt-loop",
                  "day": "2026-07-10",
                  "time": "21:00",
                  "price": "10.00",
                  "price_preorder": null,
                  "ticket_link": null,
                  "bands": [],
                  "venues": {
                    "id": 99,
                    "name": "Loop Sala",
                    "latitude": "40.0",
                    "longitude": "-3.0",
                    "image": null
                  }
                }
              ]
            }
            """.formatted(wireMock.port());

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(pageBody)));

        assertThatNoException().isThrownBy(
            () -> adapter.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
        );

        // Must have stopped at MAX_PAGES (100), not gone on forever
        wireMock.verify(com.github.tomakehurst.wiremock.client.WireMock.lessThanOrExactly(100),
            getRequestedFor(urlPathEqualTo("/api/v1/upcoming_events/")));
    }

    // Phase 2.3 — empty bands[] → concert with empty artistIds, no artist in snapshot
    @Test
    void fetch_emptyBandsProducesConcertWithEmptyArtistIds() {
        String body = """
            {
              "meta": { "next": null },
              "events": [
                {
                  "event_uid": "evt-noband",
                  "day": "2026-07-15",
                  "time": "20:00",
                  "price": "8.00",
                  "price_preorder": null,
                  "ticket_link": null,
                  "bands": [],
                  "venues": {
                    "id": 5,
                    "name": "Sala Vacia",
                    "latitude": "40.500",
                    "longitude": "-3.400",
                    "image": null
                  }
                }
              ]
            }
            """;

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

        AlcalaSnapshot snapshot = adapter.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(snapshot.concerts()).hasSize(1);
        assertThat(snapshot.concerts().get(0).artistIds()).isEmpty();
        assertThat(snapshot.artists()).isEmpty();
    }

    // Phase 2.4 — unknown-field tolerance: extra field must not cause exception
    @Test
    void fetch_unknownFieldsInResponseDoNotCauseException() {
        String body = """
            {
              "meta": { "next": null, "unknown_meta_field": "ignored" },
              "events": [
                {
                  "event_uid": "evt-unk",
                  "day": "2026-07-20",
                  "time": "19:00",
                  "price": "5.00",
                  "price_preorder": null,
                  "ticket_link": null,
                  "some_unknown_field": "should be ignored",
                  "poster": "https://example.com/poster.jpg",
                  "bands": [
                    { "id": 7, "name": "Test Band", "slug": "test-band", "extra_band_field": "ignored" }
                  ],
                  "venues": {
                    "id": 3,
                    "name": "Sala Extra",
                    "latitude": "40.481",
                    "longitude": "-3.364",
                    "image": null,
                    "extra_venue_field": "ignored"
                  }
                }
              ]
            }
            """;

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

        assertThatNoException().isThrownBy(
            () -> adapter.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
        );

        AlcalaSnapshot snapshot = adapter.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        assertThat(snapshot.concerts()).hasSize(1);
        assertThat(snapshot.concerts().get(0).id()).isEqualTo("aem-event-evt-unk");
    }

    // Phase 2.5 — field mapping: assert each field lands in the right domain field
    @Test
    void fetch_mapsApiFieldsToDomainCorrectly() {
        String body = """
            {
              "meta": { "next": null },
              "events": [
                {
                  "event_uid": "evt-mapping",
                  "day": "2026-08-05",
                  "time": "21:30",
                  "price": "20.00",
                  "price_preorder": "18.00",
                  "ticket_link": "https://tickets.example.com/evt-mapping",
                  "poster": "https://example.com/poster-mapping.jpg",
                  "bands": [
                    { "id": 42, "name": "Mapping Band", "slug": "mapping-band" }
                  ],
                  "venues": {
                    "id": 10,
                    "name": "Sala Mapping",
                    "latitude": "40.481",
                    "longitude": "-3.364",
                    "image": "https://example.com/sala-mapping.jpg"
                  }
                }
              ]
            }
            """;

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

        AlcalaSnapshot snapshot = adapter.fetch(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(snapshot.concerts()).hasSize(1);
        var concert = snapshot.concerts().get(0);
        assertThat(concert.date().toString()).isEqualTo("2026-08-05");   // day → date
        assertThat(concert.time()).isEqualTo("21:30");                    // time → time
        assertThat(concert.price()).isEqualTo("20.00");                  // price → price
        assertThat(concert.sourceUrl()).isEqualTo("https://tickets.example.com/evt-mapping"); // ticket_link → sourceUrl

        assertThat(snapshot.venues()).hasSize(1);
        var venue = snapshot.venues().get(0);
        assertThat(venue.imageUrl()).isEqualTo("https://example.com/sala-mapping.jpg"); // venues.image → imageUrl
        assertThat(venue.lat()).isEqualTo(40.481);   // venues.latitude → lat
        assertThat(venue.lng()).isEqualTo(-3.364);   // venues.longitude → lng
        assertThat(venue.city()).isNull();            // city = null (set by use case, D9)
        assertThat(venue.province()).isNull();        // province = null (set by use case, D9)

        // poster is NOT mapped — Concert has no image/poster field
        // (verified by the Concert record having no such field)
        assertThat(snapshot.artists()).hasSize(1);
        assertThat(snapshot.artists().get(0).id()).isEqualTo("aem-band-42");
    }

    // Phase C1.1 — derive single artist from title when bands[] is empty
    @Test
    void fetch_derivesSingleArtistFromTitleWhenBandsEmpty() {
        String body = """
            {
              "meta": { "next": null },
              "events": [
                {
                  "event_uid": "evt-title",
                  "day": "2026-07-20",
                  "time": "21:00",
                  "price": "10.00",
                  "price_preorder": null,
                  "ticket_link": null,
                  "title": "QUARTET TARANTINO",
                  "bands": [],
                  "venues": {
                    "id": 7,
                    "name": "Sala Alcala",
                    "latitude": "40.481",
                    "longitude": "-3.364",
                    "image": null
                  }
                }
              ]
            }
            """;

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

        AlcalaSnapshot snapshot = adapter.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(snapshot.artists()).hasSize(1);
        assertThat(snapshot.artists().get(0).id()).isEqualTo("aem-artist-quartet-tarantino");
        assertThat(snapshot.artists().get(0).name()).isEqualTo("QUARTET TARANTINO");
        assertThat(snapshot.concerts()).hasSize(1);
        assertThat(snapshot.concerts().get(0).artistIds()).containsExactly("aem-artist-quartet-tarantino");
    }

    // Phase C2.1 — blank title + empty bands → empty artistIds (edge case)
    @Test
    void fetch_blankTitleAndEmptyBandsProduceEmptyArtistIds() {
        String body = """
            {
              "meta": { "next": null },
              "events": [
                {
                  "event_uid": "evt-blank-title",
                  "day": "2026-07-20",
                  "time": "21:00",
                  "price": "10.00",
                  "price_preorder": null,
                  "ticket_link": null,
                  "title": "",
                  "bands": [],
                  "venues": {
                    "id": 8,
                    "name": "Sala Alcala",
                    "latitude": "40.481",
                    "longitude": "-3.364",
                    "image": null
                  }
                }
              ]
            }
            """;

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

        AlcalaSnapshot snapshot = adapter.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(snapshot.artists()).isEmpty();
        assertThat(snapshot.concerts()).hasSize(1);
        assertThat(snapshot.concerts().get(0).artistIds()).isEmpty();
    }

    // FIX 2 — malformed day skips that event without aborting the sync
    @Test
    void fetch_malformedDaySkipsEventAndDoesNotThrow() {
        String body = """
            {
              "meta": { "next": null },
              "events": [
                {
                  "event_uid": "evt-bad-date",
                  "day": "not-a-date",
                  "time": "21:00",
                  "price": "10.00",
                  "price_preorder": null,
                  "ticket_link": null,
                  "bands": [
                    { "id": 11, "name": "Bad Date Band", "slug": "bad-date-band" }
                  ],
                  "venues": {
                    "id": 11,
                    "name": "Sala Bad",
                    "latitude": "40.481",
                    "longitude": "-3.364",
                    "image": null
                  }
                },
                {
                  "event_uid": "evt-good",
                  "day": "2026-07-15",
                  "time": "22:00",
                  "price": "12.00",
                  "price_preorder": null,
                  "ticket_link": null,
                  "bands": [
                    { "id": 12, "name": "Good Band", "slug": "good-band" }
                  ],
                  "venues": {
                    "id": 12,
                    "name": "Sala Good",
                    "latitude": "40.490",
                    "longitude": "-3.370",
                    "image": null
                  }
                }
              ]
            }
            """;

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

        AlcalaSnapshot snapshot = adapter.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // Must not throw; malformed event is skipped; valid event is present
        assertThat(snapshot.concerts()).hasSize(1);
        assertThat(snapshot.concerts().get(0).id()).isEqualTo("aem-event-evt-good");
    }

    @Test
    void fetch_enrichesVenuesAndArtistsFromDedicatedEndpoints() {
        String eventBody = """
            {
              "meta": { "next": null },
              "events": [
                {
                  "event_uid": "evt-dedicated",
                  "day": "2026-08-05",
                  "time": "21:30",
                  "price": "20.00",
                  "price_preorder": null,
                  "ticket_link": "https://tickets.example.com/evt-dedicated",
                  "bands": [
                    { "id": 42, "name": "Nested Name", "slug": "nested-name" }
                  ],
                  "venues": {
                    "id": 10,
                    "name": "Nested Venue",
                    "latitude": "40.000",
                    "longitude": "-3.000",
                    "image": null
                  }
                }
              ]
            }
            """;

        String upcomingVenuesBody = """
            [
              {
                "id": 10,
                "name": "Sala Dedicated",
                "address": "C/ Goya 6, Alcalá de Henares",
                "latitude": 40.481,
                "longitude": -3.364,
                "image": "/media/venue/sala.jpg",
                "description": "Dedicated venue description"
              }
            ]
            """;

        String bandsBody = """
            [
              {
                "id": 42,
                "name": "Dedicated Band",
                "genre": "Rock",
                "band_image": "/media/band/band.jpg",
                "webpage_link": "https://band.example.com",
                "description": "Dedicated artist description"
              }
            ]
            """;

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_events/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(eventBody)));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/upcoming_venues/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(upcomingVenuesBody)));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/bands/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(bandsBody)));

        AlcalaSnapshot snapshot = adapter.fetch(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/upcoming_events/")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/upcoming_venues/")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/bands/")));

        assertThat(snapshot.venues()).singleElement().satisfies(venue -> {
            assertThat(venue.id()).isEqualTo("aem-venue-10");
            assertThat(venue.name()).isEqualTo("Sala Dedicated");
            assertThat(venue.address()).isEqualTo("C/ Goya 6, Alcalá de Henares");
            assertThat(venue.lat()).isEqualTo(40.481);
            assertThat(venue.lng()).isEqualTo(-3.364);
            assertThat(venue.imageUrl()).isEqualTo("/media/venue/sala.jpg");
            assertThat(venue.description()).isEqualTo("Dedicated venue description");
        });
        assertThat(snapshot.artists()).singleElement().satisfies(artist -> {
            assertThat(artist.id()).isEqualTo("aem-band-42");
            assertThat(artist.name()).isEqualTo("Dedicated Band");
            assertThat(artist.genre()).isEqualTo("Rock");
            assertThat(artist.imageUrl()).isEqualTo("/media/band/band.jpg");
            assertThat(artist.website()).isEqualTo("https://band.example.com");
            assertThat(artist.description()).isEqualTo("Dedicated artist description");
        });
    }
}
