package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SyncMetadataSqliteAdapter implements SyncMetadataPort, SyncMetadataWritePort {

    private final JdbcTemplate jdbcTemplate;

    public SyncMetadataSqliteAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean shouldSync(String resource) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sync_meta WHERE resource = ?",
            Integer.class,
            resource
        );
        return count != null && count > 0;
    }

    @Override
    public Instant getLastModified(String resource) {
        String raw = jdbcTemplate.queryForObject(
            "SELECT last_modified FROM sync_meta WHERE resource = ?",
            String.class,
            resource
        );
        return Instant.parse(raw);
    }

    @Override
    public void updateLastModified(String resource, Instant timestamp) {
        jdbcTemplate.update("""
            INSERT INTO sync_meta(resource, last_modified) VALUES (?,?)
            ON CONFLICT(resource) DO UPDATE SET last_modified=excluded.last_modified
            """,
            resource, timestamp.toString()
        );
    }
}
