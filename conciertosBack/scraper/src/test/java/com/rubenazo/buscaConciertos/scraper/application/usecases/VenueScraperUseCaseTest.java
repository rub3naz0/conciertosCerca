package com.rubenazo.buscaConciertos.scraper.application.usecases;

import com.rubenazo.buscaConciertos.scraper.application.parsers.VenueDetailParser;
import com.rubenazo.buscaConciertos.scraper.application.parsers.VenueListParser;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueScraperUseCaseTest {

    @Mock
    private HtmlFetchPort htmlFetchPort;

    private VenueScraperUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new VenueScraperUseCase(
            htmlFetchPort,
            new VenueListParser(),
            new VenueDetailParser(),
            "https://conciertos.club"
        );
    }

    @Test
    void scrape_fetchesListAndDetailForEachVenue() throws HtmlFetchException {
        String listHtml = """
            <html><body>
            <ul class="list list-block">
              <li><a href="/bcn/locales/sala-a">Sala A</a></li>
            </ul>
            </body></html>
            """;
        String detailHtml = """
            <html><body>
            <article><div class="box">
              <h2>Sala A</h2>
              <div class="main_cols2_t2"><div class="col"><div class="box_ms">
                <p>
                  <span class="icon_svg icon_place"><svg></svg></span>
                  Barcelona<br />Barcelona<br />
                </p>
              </div></div></div>
            </div></article>
            </body></html>
            """;
        when(htmlFetchPort.fetch("https://conciertos.club/bcn/locales")).thenReturn(listHtml);
        when(htmlFetchPort.fetch("https://conciertos.club/bcn/locales/sala-a")).thenReturn(detailHtml);

        List<Discrepancy> discrepancies = new ArrayList<>();
        List<ScrapedVenue> venues = useCase.scrape(List.of("bcn"), discrepancies);

        assertThat(venues).hasSize(1);
        assertThat(venues.get(0).name()).isEqualTo("Sala A");
        assertThat(discrepancies).isEmpty();
        verify(htmlFetchPort, times(1)).fetch("https://conciertos.club/bcn/locales");
        verify(htmlFetchPort, times(1)).fetch("https://conciertos.club/bcn/locales/sala-a");
    }

    @Test
    void scrape_404OnDetailPage_recordsFetchErrorAndContinues() throws HtmlFetchException {
        String listHtml = """
            <html><body>
            <ul class="list list-block">
              <li><a href="/bcn/locales/missing">Missing</a></li>
              <li><a href="/bcn/locales/present">Present</a></li>
            </ul>
            </body></html>
            """;
        String detailHtml = """
            <html><body>
            <article><div class="box">
              <h2>Present</h2>
              <div class="main_cols2_t2"><div class="col"><div class="box_ms">
                <p>
                  <span class="icon_svg icon_place"><svg></svg></span>
                  Barcelona<br />Barcelona<br />
                </p>
              </div></div></div>
            </div></article>
            </body></html>
            """;

        when(htmlFetchPort.fetch("https://conciertos.club/bcn/locales")).thenReturn(listHtml);
        when(htmlFetchPort.fetch("https://conciertos.club/bcn/locales/missing"))
            .thenThrow(new HtmlFetchException("https://conciertos.club/bcn/locales/missing", 404, "Not Found"));
        when(htmlFetchPort.fetch("https://conciertos.club/bcn/locales/present")).thenReturn(detailHtml);

        List<Discrepancy> discrepancies = new ArrayList<>();
        List<ScrapedVenue> venues = useCase.scrape(List.of("bcn"), discrepancies);

        assertThat(venues).hasSize(1);
        assertThat(venues.get(0).name()).isEqualTo("Present");
        assertThat(discrepancies).hasSize(1);
        assertThat(discrepancies.get(0).type()).isEqualTo(DiscrepancyType.FETCH_ERROR);
    }

    @Test
    void scrape_iteratesAllProvinces() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString())).thenReturn(
            "<html><body><ul class='list list-block'></ul></body></html>"
        );

        List<Discrepancy> discrepancies = new ArrayList<>();
        useCase.scrape(List.of("bcn", "mad", "val"), discrepancies);

        verify(htmlFetchPort, times(1)).fetch("https://conciertos.club/bcn/locales");
        verify(htmlFetchPort, times(1)).fetch("https://conciertos.club/mad/locales");
        verify(htmlFetchPort, times(1)).fetch("https://conciertos.club/val/locales");
    }
}
