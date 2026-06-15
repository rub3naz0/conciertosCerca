package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.SalaConcierto;

import java.time.Instant;

public interface SalaConciertoWritePort {
    void upsert(SalaConcierto sala);

    /**
     * Inserts only if the id does not exist yet — existing rows are left untouched.
     * Required for partial salas (all-null details): an id collision with a real sala
     * must never blank out its fields.
     */
    void insertIfAbsent(SalaConcierto sala);

    void updateField(String id, String field, String value, Instant updatedAt);

    /**
     * Single UPDATE of all editable columns (name, address, city, province, lat, lng,
     * description, image_url) plus updated_at. Used by the admin edit flow — never
     * inserts, edit-only.
     */
    void updateAll(SalaConcierto sala);
}
