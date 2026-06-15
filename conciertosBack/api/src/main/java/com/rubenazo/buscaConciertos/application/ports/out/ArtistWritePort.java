package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.Artist;

import java.time.Instant;

public interface ArtistWritePort {
    void upsert(Artist artist);
    void updateField(String id, String field, String value, Instant updatedAt);
    void touchUpdatedAt(String id, Instant updatedAt);

    /**
     * Single UPDATE of all editable columns (name, genre, description, image_url)
     * plus updated_at. Used by the admin edit flow — never inserts, edit-only.
     */
    void updateAll(Artist artist);
}
