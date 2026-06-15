package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.domain.SyncRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

class SyncRunSqliteAdapterTest {

    private JdbcTemplate jdbcTemplate;
    private SyncRunSqliteAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

        // SQLite JDBC doesn't register its error codes with Spring's default translator,
        // so constraint violations surface as UncategorizedSQLException unless we
        // install a custom translator that maps SQLite error code 19 (SQLITE_CONSTRAINT)
        // to DataIntegrityViolationException — matching what Spring Boot autoconfigures
        // in production.
        SQLExceptionTranslator sqliteTranslator = (task, sql, ex) -> {
            if (ex.getErrorCode() == 19) {
                return new DataIntegrityViolationException(ex.getMessage(), ex);
            }
            return new SQLStateSQLExceptionTranslator().translate(task, sql, ex);
        };
        jdbcTemplate.setExceptionTranslator(sqliteTranslator);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sync_runs (
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL DEFAULT 'RUNNING',
                started_at TEXT NOT NULL,
                completed_at TEXT,
                salas_count INTEGER NOT NULL DEFAULT 0,
                artists_count INTEGER NOT NULL DEFAULT 0,
                concerts_count INTEGER NOT NULL DEFAULT 0,
                errors_count INTEGER NOT NULL DEFAULT 0,
                discrepancies_count INTEGER NOT NULL DEFAULT 0,
                error_message TEXT,
                created_at TEXT NOT NULL
            )
            """);

        // Mirror the schema.sql partial unique index so the adapter's constraint-based
        // concurrency guard is exercised in tests.
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_runs_single_running
                ON sync_runs(status) WHERE status = 'running'
            """);

        var txTemplate = new TransactionTemplate(new DataSourceTransactionManager(ds));
        adapter = new SyncRunSqliteAdapter(jdbcTemplate, txTemplate);
    }

    @Test
    void start_insertsRunningRowAndReturnsId() {
        String runId = adapter.start();

        assertThat(runId).isNotBlank();
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sync_runs WHERE id = ? AND status = 'running'",
            Integer.class, runId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void complete_updatesStatusAndCounts() {
        String runId = adapter.start();

        adapter.complete(runId, 5, 10, 20, 2, 3);

        SyncRun run = adapter.findById(runId).orElseThrow();
        assertThat(run.status()).isEqualTo("completed");
        assertThat(run.completedAt()).isNotNull();
        assertThat(run.salasCount()).isEqualTo(5);
        assertThat(run.artistsCount()).isEqualTo(10);
        assertThat(run.concertsCount()).isEqualTo(20);
        assertThat(run.errorsCount()).isEqualTo(2);
        assertThat(run.discrepanciesCount()).isEqualTo(3);
    }

    @Test
    void fail_updatesStatusAndErrorMessage() {
        String runId = adapter.start();

        adapter.fail(runId, "Scraper timed out");

        SyncRun run = adapter.findById(runId).orElseThrow();
        assertThat(run.status()).isEqualTo("failed");
        assertThat(run.completedAt()).isNotNull();
        assertThat(run.errorMessage()).isEqualTo("Scraper timed out");
    }

    @Test
    void findLatest_returnsEmptyWhenNoRuns() {
        Optional<SyncRun> result = adapter.findLatest();

        assertThat(result).isEmpty();
    }

    @Test
    void findLatest_returnsMostRecentRun() {
        String firstId = adapter.start();
        adapter.complete(firstId, 1, 1, 1, 0, 0);
        String secondId = adapter.start();
        adapter.complete(secondId, 2, 2, 2, 0, 0);

        Optional<SyncRun> result = adapter.findLatest();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(secondId);
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        Optional<SyncRun> result = adapter.findById("nonexistent-id");

        assertThat(result).isEmpty();
    }

    @Test
    void isRunning_returnsFalseWhenNoActiveRun() {
        assertThat(adapter.isRunning()).isFalse();
    }

    @Test
    void isRunning_returnsTrueWhenRunExists() {
        adapter.start();

        assertThat(adapter.isRunning()).isTrue();
    }

    @Test
    void isRunning_returnsFalseAfterRunCompletes() {
        String runId = adapter.start();
        adapter.complete(runId, 0, 0, 0, 0, 0);

        assertThat(adapter.isRunning()).isFalse();
    }

    @Test
    void tryStart_returnsEmptyWhenAlreadyRunning() {
        // First call must succeed
        Optional<String> first = adapter.tryStart();
        assertThat(first).isPresent();

        // Second call while the first run is still 'running' must return empty
        Optional<String> second = adapter.tryStart();
        assertThat(second).isEmpty();

        // Only one running row must exist in the DB
        Integer runningCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sync_runs WHERE status = 'running'", Integer.class
        );
        assertThat(runningCount).isEqualTo(1);
    }

    @Test
    void tryStart_allowsNewRunAfterPreviousCompletes() {
        Optional<String> first = adapter.tryStart();
        assertThat(first).isPresent();
        adapter.complete(first.get(), 0, 0, 0, 0, 0);

        Optional<String> second = adapter.tryStart();
        assertThat(second).isPresent();
    }

    // -----------------------------------------------------------------------
    // Test 1 — DB-level: unique partial index rejects a second 'running' row
    // -----------------------------------------------------------------------

    @Test
    void uniquePartialIndex_rejectsSecondRunningRowDirectly() {
        // Seed one 'running' row via the adapter (valid path)
        String firstId = adapter.start();

        // Attempt a direct raw INSERT of a second 'running' row — must be rejected
        // by the unique partial index at the DB level.
        String now = java.time.Instant.now().toString();
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO sync_runs (id, status, started_at, created_at) VALUES (?, 'running', ?, ?)",
                java.util.UUID.randomUUID().toString(), now, now
            )
        ).isInstanceOf(DataIntegrityViolationException.class);

        // Exactly one running row must still exist — no partial insert leaked through.
        Integer runningCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sync_runs WHERE status = 'running'", Integer.class);
        assertThat(runningCount).isEqualTo(1);

        // Once the first run completes (status leaves 'running'), a new 'running'
        // row must be accepted — proving the constraint is released, not permanent.
        adapter.complete(firstId, 0, 0, 0, 0, 0);

        String now2 = java.time.Instant.now().toString();
        jdbcTemplate.update(
            "INSERT INTO sync_runs (id, status, started_at, created_at) VALUES (?, 'running', ?, ?)",
            java.util.UUID.randomUUID().toString(), now2, now2
        );

        Integer afterComplete = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sync_runs WHERE status = 'running'", Integer.class);
        assertThat(afterComplete).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Test 2 — catch branch: COUNT returns 0 but INSERT violates index
    //          → tryStart() must return Optional.empty() with no DB leak
    // -----------------------------------------------------------------------

    @Test
    void tryStart_catchBranch_returnsEmptyWhenInsertViolatesIndex() {
        // Pre-seed a 'running' row directly in the DB so the index is occupied.
        String now = java.time.Instant.now().toString();
        jdbcTemplate.update(
            "INSERT INTO sync_runs (id, status, started_at, created_at) VALUES (?, 'running', ?, ?)",
            java.util.UUID.randomUUID().toString(), now, now
        );

        // Spy on the real JdbcTemplate so we can stub the COUNT query to return 0,
        // simulating a thread that read 0 before the row above was committed but now
        // hits the index on INSERT.
        JdbcTemplate spy = Mockito.spy(jdbcTemplate);
        doReturn(0).when(spy).queryForObject(
            anyString(), eq(Integer.class));

        // Build an adapter wired to the spy (COUNT will return 0) but the real
        // underlying DataSource still has the 'running' row, so the INSERT will
        // violate the unique partial index and throw DataIntegrityViolationException.
        var ds = jdbcTemplate.getDataSource();
        var txManager = new DataSourceTransactionManager(ds);
        var txTemplate = new TransactionTemplate(txManager);
        SyncRunSqliteAdapter racingAdapter = new SyncRunSqliteAdapter(spy, txTemplate);

        Optional<String> result = racingAdapter.tryStart();

        // The catch branch must map the violation to Optional.empty().
        assertThat(result).isEmpty();

        // The DB must still have exactly ONE 'running' row — no leak from the failed INSERT.
        Integer runningCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sync_runs WHERE status = 'running'", Integer.class);
        assertThat(runningCount).isEqualTo(1);
    }
}
