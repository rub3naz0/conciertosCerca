package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.SyncRun;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SyncRunSqliteAdapter implements SyncRunPort {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    private static final RowMapper<SyncRun> ROW_MAPPER = (rs, rowNum) -> {
        String completedAtStr = rs.getString("completed_at");
        String errorMessage = rs.getString("error_message");
        return new SyncRun(
            rs.getString("id"),
            rs.getString("status"),
            Instant.parse(rs.getString("started_at")),
            completedAtStr != null ? Instant.parse(completedAtStr) : null,
            rs.getInt("salas_count"),
            rs.getInt("artists_count"),
            rs.getInt("concerts_count"),
            rs.getInt("errors_count"),
            rs.getInt("discrepancies_count"),
            errorMessage,
            Instant.parse(rs.getString("created_at"))
        );
    };

    public SyncRunSqliteAdapter(JdbcTemplate jdbcTemplate, TransactionTemplate txTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = txTemplate;
    }

    @Override
    public String start() {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        jdbcTemplate.update("""
            INSERT INTO sync_runs (id, status, started_at, created_at)
            VALUES (?, 'running', ?, ?)
            """,
            id, now, now
        );
        return id;
    }

    @Override
    public Optional<String> tryStart() {
        return txTemplate.execute(status -> {
            // Cheap fast-path: avoid the insert round-trip when we already know a run is active.
            Integer running = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sync_runs WHERE status = 'running'", Integer.class);
            if (running != null && running > 0) {
                return Optional.empty();
            }
            // The unique partial index on status='running' is the real atomicity guarantee.
            // If two threads race past the COUNT check, only one INSERT succeeds; the other
            // gets a constraint violation which we treat as "already running".
            try {
                return Optional.of(start());
            } catch (DataIntegrityViolationException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public void complete(String runId, int salas, int artists, int concerts, int errors, int discrepancies) {
        String completedAt = Instant.now().toString();
        jdbcTemplate.update("""
            UPDATE sync_runs
            SET status = 'completed', completed_at = ?, error_message = NULL,
                salas_count = ?, artists_count = ?, concerts_count = ?,
                errors_count = ?, discrepancies_count = ?
            WHERE id = ?
            """,
            completedAt, salas, artists, concerts, errors, discrepancies, runId
        );
    }

    @Override
    public void fail(String runId, String errorMessage) {
        String completedAt = Instant.now().toString();
        jdbcTemplate.update("""
            UPDATE sync_runs
            SET status = 'failed', completed_at = ?, error_message = ?
            WHERE id = ?
            """,
            completedAt, errorMessage, runId
        );
    }

    @Override
    public Optional<SyncRun> findLatest() {
        List<SyncRun> results = jdbcTemplate.query(
            "SELECT * FROM sync_runs ORDER BY created_at DESC LIMIT 1",
            ROW_MAPPER
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<SyncRun> findById(String runId) {
        List<SyncRun> results = jdbcTemplate.query(
            "SELECT * FROM sync_runs WHERE id = ?",
            ROW_MAPPER,
            runId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public boolean isRunning() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sync_runs WHERE status = 'running'",
            Integer.class
        );
        return count != null && count > 0;
    }
}
