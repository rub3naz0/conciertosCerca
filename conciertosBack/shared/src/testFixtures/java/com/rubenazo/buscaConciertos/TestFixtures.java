package com.rubenazo.buscaConciertos;

import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TestFixtures {

    public static final Instant LAST_MODIFIED = Instant.parse("2026-05-20T15:30:00Z");
    public static final Instant OLD_TIMESTAMP = Instant.parse("2026-05-17T00:00:00Z");
    public static final Instant RECENT_TIMESTAMP = Instant.parse("2026-05-19T00:00:00Z");
    public static final Instant FUTURE_TIMESTAMP = Instant.parse("2026-05-22T00:00:00Z");

    private TestFixtures() {}

    public static Artist anArtist() {
        return anArtist("a1", LAST_MODIFIED);
    }

    public static Artist anArtist(String id, Instant updatedAt) {
        return new Artist(id, "Vetusta Morla", "Indie Rock", "http://img.jpg", "http://web.com", null, null, updatedAt);
    }

    public static Concert aConcert() {
        return aConcert(LocalDate.of(2026, 6, 15), LAST_MODIFIED);
    }

    public static Concert aConcert(LocalDate date, Instant updatedAt) {
        return new Concert("c1", "sala1", List.of("a1", "a2"), date, "21:00", "25€",
                "http://source", updatedAt);
    }

    public static Concert aConcert(String id, LocalDate date, Instant updatedAt) {
        return new Concert(id, "sala1", List.of("a1"), date, "21:00", "25€",
                "http://source", updatedAt);
    }

    public static SalaConcierto aSala() {
        return aSala("s1", LAST_MODIFIED);
    }

    public static SalaConcierto aSala(String id, Instant updatedAt) {
        return new SalaConcierto(id, "Sala Apolo", "C/ Nou de la Rambla 113", "Barcelona",
                "Barcelona", 41.3735, 2.1700, "http://img.jpg", null, null, updatedAt);
    }
}
