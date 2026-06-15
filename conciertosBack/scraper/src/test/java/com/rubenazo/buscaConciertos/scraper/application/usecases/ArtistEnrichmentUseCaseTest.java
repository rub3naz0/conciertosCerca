package com.rubenazo.buscaConciertos.scraper.application.usecases;

import com.rubenazo.buscaConciertos.scraper.application.parsers.ArtistDetailParser;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedArtist;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedConcert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistEnrichmentUseCaseTest {

    @Mock
    private HtmlFetchPort htmlFetchPort;

    private ArtistEnrichmentUseCase useCase;

    private static final String CONCERT_DETAIL_HTML = """
        <html><body>
        <div class="texto">
        <h3>Vetusta Morla</h3>
        <p>Una gran banda.</p>
        </div>
        </body></html>
        """;

    private static final String CONCERT_DETAIL_WITH_IMAGE_HTML = """
        <html><body>
        <img itemprop="image" src="/doc/a/2023/a_russianred.jpg" />
        <div class="texto">
        <h3>Russian Red</h3>
        <p>Una gran artista.</p>
        </div>
        </body></html>
        """;

    @BeforeEach
    void setUp() {
        useCase = new ArtistEnrichmentUseCase(
            htmlFetchPort,
            new ArtistDetailParser(),
            "https://conciertos.club"
        );
    }

    private ScrapedConcert concert(String artistName, String sourceUrl) {
        return new ScrapedConcert(
            "c1", null, List.of(), LocalDate.of(2026, 7, 1), "21:00",
            "25€", sourceUrl, "Sala", "Barcelona",
            artistName, "Rock", "/img/artist.jpg", null
        );
    }

    @Test
    void enrich_deduplicatesArtistNames() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString())).thenReturn(CONCERT_DETAIL_HTML);

        List<ScrapedConcert> concerts = List.of(
            concert("Vetusta Morla", "/madrid/conciertos/12345-vetusta-morla/123"),
            concert("Vetusta Morla", "/madrid/conciertos/99999-vetusta-morla/456")
        );
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<ScrapedArtist> artists = useCase.enrich(concerts, Set.of(), discrepancies);

        assertThat(artists).hasSize(1);
        verify(htmlFetchPort, times(1)).fetch(anyString());
    }

    @Test
    void enrich_skipsAlreadyKnownArtistSlugs() throws HtmlFetchException {
        List<ScrapedConcert> concerts = List.of(
            concert("Vetusta Morla", "/madrid/conciertos/12345-vetusta-morla/123")
        );
        Set<String> known = Set.of("vetusta-morla");
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<ScrapedArtist> artists = useCase.enrich(concerts, known, discrepancies);

        assertThat(artists).isEmpty();
        verifyNoInteractions(htmlFetchPort);
    }

    @Test
    void enrich_fetchError_recordsArtistNotFound() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString()))
            .thenThrow(new HtmlFetchException("https://url", 404, "Not Found"));

        List<ScrapedConcert> concerts = List.of(
            concert("Unknown Artist", "/madrid/conciertos/99-unknown/123")
        );
        List<Discrepancy> discrepancies = new ArrayList<>();
        List<ScrapedArtist> artists = useCase.enrich(concerts, Set.of(), discrepancies);

        assertThat(artists).hasSize(1); // still added, just with null description
        assertThat(discrepancies).hasSize(1);
        assertThat(discrepancies.get(0).type()).isEqualTo(DiscrepancyType.ARTIST_NOT_FOUND);
    }

    @Test
    void enrich_callCountMatchesUniqueArtistNames() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString())).thenReturn(CONCERT_DETAIL_HTML);

        List<ScrapedConcert> concerts = List.of(
            concert("Artist One", "/madrid/conciertos/1-artist-one/111"),
            concert("Artist Two", "/madrid/conciertos/2-artist-two/222"),
            concert("Artist Three", "/madrid/conciertos/3-artist-three/333")
        );
        useCase.enrich(concerts, Set.of(), new ArrayList<>());

        verify(htmlFetchPort, times(3)).fetch(anyString());
    }

    // This existing test will FAIL after task 6.4 changes imageUrl to come from detail page.
    // The detail HTML (CONCERT_DETAIL_HTML) has no img[itemprop="image"], so imageUrl falls
    // back to concert.imageUrl(). This test stays valid.
    @Test
    void enrich_buildsScrapedArtistFromConcertData() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString())).thenReturn(CONCERT_DETAIL_HTML);

        List<ScrapedConcert> concerts = List.of(
            concert("Vetusta Morla", "https://conciertos.club/madrid/conciertos/12345-vetusta-morla/123")
        );
        List<ScrapedArtist> artists = useCase.enrich(concerts, Set.of(), new ArrayList<>());

        assertThat(artists).hasSize(1);
        ScrapedArtist artist = artists.get(0);
        assertThat(artist.id()).isEqualTo("vetusta-morla");
        assertThat(artist.name()).isEqualTo("Vetusta Morla");
        assertThat(artist.genre()).isEqualTo("Rock");
        // No img[itemprop="image"] in detail HTML → falls back to concert.imageUrl()
        assertThat(artist.imageUrl()).isEqualTo("/img/artist.jpg");
        assertThat(artist.description()).contains("Una gran banda.");
    }

    // 6.3 [RED] enrich uses ArtistDetail.imageUrl() from detail page, NOT concert.imageUrl()
    @Test
    void enrich_usesDetailPageImageUrl_notConcertListThumbnail() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString())).thenReturn(CONCERT_DETAIL_WITH_IMAGE_HTML);

        List<ScrapedConcert> concerts = List.of(
            concert("Russian Red", "https://conciertos.club/madrid/conciertos/1-russian-red/123")
        );
        List<ScrapedArtist> artists = useCase.enrich(concerts, Set.of(), new ArrayList<>());

        assertThat(artists).hasSize(1);
        ScrapedArtist artist = artists.get(0);
        assertThat(artist.imageUrl()).isEqualTo("https://conciertos.club/doc/a/2023/a_russianred.jpg");
        assertThat(artist.imageUrl()).doesNotContain("/img/artist.jpg");
    }
}
