package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 7.1 [RED] Integration test for SalaConciertoSqliteAdapter:
 * - upsert venue with description, query back, assert description present
 * - assert no phone/website columns
 * Task 7.5 [GREEN] verifies the adapter compiles and works with new schema.
 */
class SalaConciertoSqliteAdapterFieldAlignmentTest {

    private JdbcTemplate jdbcTemplate;
    private SalaConciertoSqliteAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

        // New schema: description added, phone/website removed
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS salas_concierto (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT,
                city TEXT NOT NULL, province TEXT NOT NULL,
                lat REAL, lng REAL, description TEXT, image_url TEXT,
                source_url TEXT, updated_at TEXT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS data_quality (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
                field TEXT NOT NULL, status TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'non_severe',
                suggested TEXT, source TEXT, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        adapter = new SalaConciertoSqliteAdapter(jdbcTemplate);
    }

    @Test
    void upsert_storesDescriptionAndCanQueryItBack() {
        var sala = new SalaConcierto(
            "s1", "Sala Apolo", "Carrer de la Nou de la Rambla, 113", "Barcelona", "Barcelona",
            41.374, 2.169,
            "https://img.sala-apolo.com/logo.jpg",
            "Historic concert venue in Barcelona founded in 1943",
            "https://sala-apolo.com",
            Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.upsert(sala);

        List<SalaConcierto> result = adapter.findAll();
        assertThat(result).hasSize(1);
        SalaConcierto found = result.get(0);
        assertThat(found.description()).isEqualTo("Historic concert venue in Barcelona founded in 1943");
        assertThat(found.imageUrl()).isEqualTo("https://img.sala-apolo.com/logo.jpg");
    }

    @Test
    void upsert_handlesNullDescription() {
        var sala = new SalaConcierto(
            "s2", "Minimal Sala", null, "Valencia", "Valencia",
            null, null, null, null, "https://source.test",
            Instant.parse("2026-05-21T00:00:00Z")
        );

        adapter.upsert(sala);

        List<SalaConcierto> result = adapter.findAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).isNull();
    }

    @Test
    void schema_hasNoPhoneOrWebsiteColumns() {
        // Insert without phone/website — should not fail since those columns don't exist
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            "s1", "Test Sala", "Madrid", "Madrid", "2026-05-20T10:00:00Z"
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM salas_concierto WHERE id = 's1'");
        assertThat(rows).hasSize(1);
        // phone and website columns must not exist in the result set
        assertThat(rows.get(0)).doesNotContainKey("phone");
        assertThat(rows.get(0)).doesNotContainKey("website");
        // description column must exist (even if null)
        assertThat(rows.get(0)).containsKey("description");
    }

    @Test
    void findModifiedAfter_returnsVenuesWithDescription() {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, description, updated_at) VALUES (?,?,?,?,?,?)",
            "s1", "Sala With Desc", "Bilbao", "Vizcaya",
            "A great venue in Bilbao", "2026-05-19T00:00:00Z"
        );

        Instant since = Instant.parse("2026-05-18T00:00:00Z");
        List<SalaConcierto> result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).isEqualTo("A great venue in Bilbao");
    }
}
