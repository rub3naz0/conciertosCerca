package com.rubenazo.buscaConciertos.scraper.adapters.out;

import com.rubenazo.buscaConciertos.scraper.application.ports.out.ExistingDataReaderPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ExistingDataSqliteReaderAdapter implements ExistingDataReaderPort {

    private final JdbcTemplate jdbcTemplate;

    public ExistingDataSqliteReaderAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> existingConcertIds() {
        return queryIds("SELECT id FROM concerts WHERE deleted = 0");
    }

    @Override
    public Set<String> existingArtistIds() {
        return queryIds("SELECT id FROM artists");
    }

    @Override
    public Set<String> enrichedArtistIds() {
        return queryIds("SELECT id FROM artists WHERE description IS NOT NULL AND description != ''");
    }

    @Override
    public Set<String> existingVenueIds() {
        return queryIds("SELECT id FROM salas_concierto");
    }

    private Set<String> queryIds(String sql) {
        List<String> ids = jdbcTemplate.queryForList(sql, String.class);
        return new HashSet<>(ids);
    }
}
