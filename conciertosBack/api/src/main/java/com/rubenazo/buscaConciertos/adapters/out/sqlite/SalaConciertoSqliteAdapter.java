package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class SalaConciertoSqliteAdapter implements SalaConciertoRepositoryPort, SalaConciertoWritePort {

    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> ALLOWED_FIELDS = Set.of("name", "address", "city", "province", "lat", "lng", "description", "image_url");

    private static final RowMapper<SalaConcierto> ROW_MAPPER = (rs, rowNum) -> new SalaConcierto(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("address"),
        rs.getString("city"),
        rs.getString("province"),
        rs.getObject("lat") != null ? rs.getDouble("lat") : null,
        rs.getObject("lng") != null ? rs.getDouble("lng") : null,
        rs.getString("image_url"),
        rs.getString("description"),
        rs.getString("source_url"),
        Instant.parse(rs.getString("updated_at"))
    );

    public SalaConciertoSqliteAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String ELIGIBILITY_CONDITION = """
            AND NOT EXISTS (
                SELECT 1 FROM data_quality dq
                WHERE dq.entity_type = 'sala' AND dq.entity_id = salas_concierto.id AND dq.severity = 'severe'
                  AND dq.status NOT IN ('approved','auto_approved')
            )
        """;

    @Override
    public List<SalaConcierto> findAll() {
        return jdbcTemplate.query(
            "SELECT id, name, address, city, province, lat, lng, description, image_url, source_url, updated_at FROM salas_concierto WHERE 1=1"
                + ELIGIBILITY_CONDITION,
            ROW_MAPPER
        );
    }

    @Override
    public List<SalaConcierto> findModifiedAfter(Instant since) {
        return jdbcTemplate.query(
            "SELECT id, name, address, city, province, lat, lng, description, image_url, source_url, updated_at FROM salas_concierto WHERE updated_at >= ?"
                + ELIGIBILITY_CONDITION,
            ROW_MAPPER,
            since.toString()
        );
    }

    @Override
    public List<SalaConcierto> findAllIncludingBlocked() {
        return jdbcTemplate.query(
            "SELECT id, name, address, city, province, lat, lng, description, image_url, source_url, updated_at FROM salas_concierto",
            ROW_MAPPER
        );
    }

    @Override
    public Optional<SalaConcierto> findByIdIncludingBlocked(String id) {
        List<SalaConcierto> results = jdbcTemplate.query(
            "SELECT id, name, address, city, province, lat, lng, description, image_url, source_url, updated_at FROM salas_concierto WHERE id = ?",
            ROW_MAPPER,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void upsert(SalaConcierto sala) {
        jdbcTemplate.update("""
            INSERT INTO salas_concierto(id, name, address, city, province, lat, lng, description, image_url, source_url, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name, address=excluded.address, city=excluded.city,
                province=excluded.province, lat=excluded.lat, lng=excluded.lng,
                description=excluded.description, image_url=excluded.image_url,
                source_url=excluded.source_url, updated_at=excluded.updated_at
            """,
            sala.id(), sala.name(), sala.address(), sala.city(), sala.province(),
            sala.lat(), sala.lng(), sala.description(), sala.imageUrl(),
            sala.sourceUrl(), sala.updatedAt().toString()
        );
    }

    @Override
    public void insertIfAbsent(SalaConcierto sala) {
        jdbcTemplate.update("""
            INSERT INTO salas_concierto(id, name, address, city, province, lat, lng, description, image_url, source_url, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO NOTHING
            """,
            sala.id(), sala.name(), sala.address(), sala.city(), sala.province(),
            sala.lat(), sala.lng(), sala.description(), sala.imageUrl(),
            sala.sourceUrl(), sala.updatedAt().toString()
        );
    }

    @Override
    public void updateField(String id, String field, String value, Instant updatedAt) {
        if (!ALLOWED_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Invalid field name for salas_concierto table: " + field);
        }
        jdbcTemplate.update(
            "UPDATE salas_concierto SET " + field + " = ?, updated_at = ? WHERE id = ?",
            value, updatedAt.toString(), id
        );
    }

    @Override
    public void updateAll(SalaConcierto sala) {
        jdbcTemplate.update("""
            UPDATE salas_concierto SET
                name = ?, address = ?, city = ?, province = ?,
                lat = ?, lng = ?, description = ?, image_url = ?,
                updated_at = ?
            WHERE id = ?
            """,
            sala.name(), sala.address(), sala.city(), sala.province(),
            sala.lat(), sala.lng(), sala.description(), sala.imageUrl(),
            sala.updatedAt().toString(), sala.id()
        );
    }
}
