package com.rubenazo.buscaConciertos.scraper.application.usecases;

import com.rubenazo.buscaConciertos.scraper.application.parsers.ConcertListParser;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedConcert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertScraperUseCaseTest {

    @Mock
    private HtmlFetchPort htmlFetchPort;

    private ConcertScraperUseCase useCase;

    private static final String EMPTY_HTML =
        "<html><body><section class='conciertos'><ul class='list'></ul></section></body></html>";

    @BeforeEach
    void setUp() {
        useCase = new ConcertScraperUseCase(
            htmlFetchPort,
            new ConcertListParser(),
            "https://conciertos.club",
            1  // chunk-months = 1
        );
    }

    @Test
    void scrape_splitDateRangeIntoMonthlyChunks() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString())).thenReturn(EMPTY_HTML);

        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 8, 1);  // 2 months

        List<Discrepancy> discrepancies = new ArrayList<>();
        useCase.scrape(start, end, discrepancies);

        // Should make 2 calls: June and July
        verify(htmlFetchPort, times(2)).fetch(anyString());
    }

    @Test
    void scrape_oneMonthRange_makesOneCall() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString())).thenReturn(EMPTY_HTML);

        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        List<Discrepancy> discrepancies = new ArrayList<>();
        List<ScrapedConcert> concerts = useCase.scrape(start, end, discrepancies);

        verify(htmlFetchPort, times(1)).fetch(anyString());
        assertThat(concerts).isEmpty();
    }

    @Test
    void scrape_urlContainsDateParameters() throws HtmlFetchException {
        when(htmlFetchPort.fetch(anyString())).thenReturn(EMPTY_HTML);

        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        useCase.scrape(start, end, new ArrayList<>());

        verify(htmlFetchPort).fetch(argThat(url ->
            url.contains("search.php") && url.contains("fecha1") && url.contains("fecha2")
        ));
    }
}
