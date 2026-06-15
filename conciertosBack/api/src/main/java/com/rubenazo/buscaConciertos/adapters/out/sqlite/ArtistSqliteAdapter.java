package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.application.ports.out.ArtistRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.domain.Artist;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ArtistSqliteAdapter implements ArtistRepositoryPort, ArtistWritePort {

    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> ALLOWED_FIELDS = Set.of("name", "genre", "image_url", "website", "description");

    private static final RowMapper<Artist> ROW_MAPPER = (rs, rowNum) -> new Artist(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("genre"),
        rs.getString("image_url"),
        rs.getString("website"),
        rs.getString("source_url"),
        rs.getString("description"),
        Instant.parse(rs.getString("updated_at"))
    );

    public ArtistSqliteAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String ELIGIBILITY_CONDITION = """
             AND NOT EXISTS (
                SELECT 1 FROM data_quality dq
                WHERE dq.entity_type = 'artist' AND dq.entity_id = artists.id AND dq.severity = 'severe'
                  AND dq.status NOT IN ('approved','auto_approved')
            )
        """;

    @Override
    public List<Artist> findAll() {
        return jdbcTemplate.query(
            "SELECT id, name, genre, image_url, website, source_url, description, updated_at FROM artists WHERE 1=1"
                + ELIGIBILITY_CONDITION,
            ROW_MAPPER);
    }

    @Override
    public List<Artist> findModifiedAfter(Instant since) {
        return jdbcTemplate.query(
            "SELECT id, name, genre, image_url, website, source_url, description, updated_at FROM artists WHERE updated_at >= ?"
                + ELIGIBILITY_CONDITION,
            ROW_MAPPER,
            since.toString()
        );
    }

    @Override
    public void upsert(Artist artist) {
        jdbcTemplate.update("""
            INSERT INTO artists(id, name, genre, image_url, website, description, source_url, updated_at)
            VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name, genre=excluded.genre, image_url=excluded.image_url,
                website=excluded.website, description=excluded.description,
                source_url=excluded.source_url, updated_at=excluded.updated_at
            """,
            artist.id(), artist.name(), artist.genre(), artist.imageUrl(), artist.website(),
            artist.description(), artist.sourceUrl(), artist.updatedAt().toString()
        );
    }

    @Override
    public void touchUpdatedAt(String id, Instant updatedAt) {
        jdbcTemplate.update("UPDATE artists SET updated_at = ? WHERE id = ?", updatedAt.toString(), id);
    }

    @Override
    public boolean existsById(String id) {
        List<String> rows = jdbcTemplate.query(
            "SELECT 1 FROM artists WHERE id = ? LIMIT 1",
            (rs, rowNum) -> rs.getString(1),
            id
        );
        return !rows.isEmpty();
    }

    @Override
    public void updateField(String id, String field, String value, Instant updatedAt) {
        if (!ALLOWED_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Invalid field name for artists table: " + field);
        }
        jdbcTemplate.update(
            "UPDATE artists SET " + field + " = ?, updated_at = ? WHERE id = ?",
            value, updatedAt.toString(), id
        );
    }

    @Override
    public void updateAll(Artist artist) {
        jdbcTemplate.update("""
            UPDATE artists SET
                name = ?, genre = ?, description = ?, image_url = ?,
                updated_at = ?
            WHERE id = ?
            """,
            artist.name(), artist.genre(), artist.description(), artist.imageUrl(),
            artist.updatedAt().toString(), artist.id()
        );
    }

    @Override
    public List<Artist> findAllIncludingBlocked() {
        return jdbcTemplate.query(
            "SELECT id, name, genre, image_url, website, source_url, description, updated_at FROM artists",
            ROW_MAPPER
        );
    }

    @Override
    public Optional<Artist> findByIdIncludingBlocked(String id) {
        List<Artist> results = jdbcTemplate.query(
            "SELECT id, name, genre, image_url, website, source_url, description, updated_at FROM artists WHERE id = ?",
            ROW_MAPPER,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
