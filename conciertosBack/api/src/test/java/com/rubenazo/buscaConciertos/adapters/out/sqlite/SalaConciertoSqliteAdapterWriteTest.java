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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalaConciertoSqliteAdapterWriteTest {

    private JdbcTemplate jdbcTemplate;
    private SalaConciertoSqliteAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS salas_concierto (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT,
                city TEXT NOT NULL, province TEXT NOT NULL,
                lat REAL, lng REAL, description TEXT, image_url TEXT,
                source_url TEXT, updated_at TEXT NOT NULL
            )
            """);

        adapter = new SalaConciertoSqliteAdapter(jdbcTemplate);
    }

    @Test
    void upsert_insertsNewSala() {
        var sala = new SalaConcierto(
            "s1", "Sala Apolo", "Carrer de la Nou de la Rambla, 113", "Barcelona", "Barcelona",
            41.374, 2.169,
            "https://img.sala-apolo.com/logo.jpg",
            "Historic concert venue",
            null, Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.upsert(sala);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM salas_concierto WHERE id = 's1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("id", "s1");
        assertThat(rows.get(0)).containsEntry("name", "Sala Apolo");
        assertThat(rows.get(0)).containsEntry("city", "Barcelona");
    }

    @Test
    void upsert_updatesExistingSala() {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            "s1", "Old Name", "OldCity", "OldProvince", "2026-01-01T00:00:00Z"
        );

        var sala = new SalaConcierto(
            "s1", "New Name", null, "NewCity", "NewProvince",
            null, null, null, null, null, Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.upsert(sala);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM salas_concierto WHERE id = 's1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("name", "New Name");
        assertThat(rows.get(0)).containsEntry("city", "NewCity");
    }

    @Test
    void insertIfAbsent_insertsNewSala() {
        var sala = new SalaConcierto(
            "s1", "Sala Apolo", null, "Barcelona", "Barcelona",
            null, null, null, null, null, Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.insertIfAbsent(sala);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM salas_concierto WHERE id = 's1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("name", "Sala Apolo");
    }

    @Test
    void insertIfAbsent_neverOverwritesExistingSala() {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, address, city, province, lat, lng, updated_at) VALUES (?,?,?,?,?,?,?,?)",
            "granada-lemon-rock", "Lemon Rock", "Calle Montalbán 6", "Granada", "granada",
            37.17723, -3.60387, "2026-06-04T11:43:50Z"
        );

        // A partial sala (all-null details) colliding with the existing id
        var partial = new SalaConcierto(
            "granada-lemon-rock", "Lemon Rock", null, "granada", "granada",
            null, null, null, null, null, Instant.parse("2026-06-08T14:50:20Z")
        );

        adapter.insertIfAbsent(partial);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM salas_concierto WHERE id = 'granada-lemon-rock'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("address", "Calle Montalbán 6");
        assertThat(rows.get(0)).containsEntry("lat", 37.17723);
        assertThat(rows.get(0)).containsEntry("lng", -3.60387);
        assertThat(rows.get(0)).containsEntry("updated_at", "2026-06-04T11:43:50Z");
    }

    @Test
    void upsert_handlesNullOptionalFields() {
        var sala = new SalaConcierto(
            "s2", "Minimal Sala", null, "Valencia", "Valencia",
            null, null, null, null, null, Instant.parse("2026-05-21T00:00:00Z")
        );

        adapter.upsert(sala);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM salas_concierto WHERE id = 's2'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("lat")).isNull();
        assertThat(rows.get(0).get("lng")).isNull();
        assertThat(rows.get(0).get("description")).isNull();
        assertThat(rows.get(0).get("image_url")).isNull();
    }

    @Test
    void upsert_storesDescriptionCorrectly() {
        var sala = new SalaConcierto(
            "s1", "Sala Apolo", null, "Barcelona", "Barcelona",
            null, null, "https://img.jpg", "A legendary music venue", null,
            Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.upsert(sala);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT description FROM salas_concierto WHERE id = 's1'");
        assertThat(rows.get(0)).containsEntry("description", "A legendary music venue");
    }

    @Test
    void updateField_updatesDescriptionField() {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            "s1", "Sala Apolo", "Barcelona", "Barcelona", "2026-01-01T00:00:00Z"
        );

        adapter.updateField("s1", "description", "Updated description", Instant.parse("2026-05-28T10:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM salas_concierto WHERE id = 's1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("description", "Updated description");
        assertThat(rows.get(0)).containsEntry("updated_at", "2026-05-28T10:00:00Z");
    }

    @Test
    void updateField_updatesImageUrlField() {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            "s1", "Sala Apolo", "Barcelona", "Barcelona", "2026-01-01T00:00:00Z"
        );

        adapter.updateField("s1", "image_url", "https://img.new.jpg", Instant.parse("2026-05-28T10:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM salas_concierto WHERE id = 's1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("image_url", "https://img.new.jpg");
    }

    @Test
    void updateField_rejectsPhoneAsRemovedField() {
        // phone is no longer in ALLOWED_FIELDS — must be rejected
        assertThatThrownBy(() ->
            adapter.updateField("s1", "phone", "+34 933", Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateField_rejectsWebsiteAsRemovedField() {
        // website is no longer in ALLOWED_FIELDS — must be rejected
        assertThatThrownBy(() ->
            adapter.updateField("s1", "website", "https://sala.com", Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateField_rejectsUnknownColumnName() {
        assertThatThrownBy(() ->
            adapter.updateField("s1", "malicious_column; DROP TABLE salas_concierto;--", "value", Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateField_rejectsEmptyFieldName() {
        assertThatThrownBy(() ->
            adapter.updateField("s1", "", "value", Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
