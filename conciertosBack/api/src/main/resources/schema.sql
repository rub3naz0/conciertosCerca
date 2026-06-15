CREATE TABLE IF NOT EXISTS salas_concierto (
    id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT,
    city TEXT NOT NULL, province TEXT NOT NULL,
    lat REAL, lng REAL, description TEXT, image_url TEXT,
    source_url TEXT, updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_salas_city ON salas_concierto(city);

CREATE TABLE IF NOT EXISTS artists (
    id TEXT PRIMARY KEY, name TEXT NOT NULL, genre TEXT,
    image_url TEXT, website TEXT, description TEXT, source_url TEXT, updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS concerts (
    id TEXT PRIMARY KEY, sala_concierto_id TEXT NOT NULL REFERENCES salas_concierto(id),
    date TEXT NOT NULL, time TEXT, price TEXT,
    source_url TEXT NOT NULL, updated_at TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_concerts_date ON concerts(date);
CREATE INDEX IF NOT EXISTS idx_concerts_sala ON concerts(sala_concierto_id);
CREATE INDEX IF NOT EXISTS idx_concerts_updated ON concerts(updated_at);

CREATE TABLE IF NOT EXISTS concert_artists (
    concert_id TEXT NOT NULL REFERENCES concerts(id),
    artist_id TEXT NOT NULL REFERENCES artists(id),
    position INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (concert_id, artist_id)
);

CREATE TABLE IF NOT EXISTS sync_meta (
    resource TEXT PRIMARY KEY, last_modified TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS data_quality (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
    field TEXT NOT NULL, status TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'non_severe',
    suggested TEXT, source TEXT, score REAL, updated_at TEXT NOT NULL,
    UNIQUE(entity_type, entity_id, field)
);
CREATE INDEX IF NOT EXISTS idx_quality_status ON data_quality(status);
CREATE INDEX IF NOT EXISTS idx_quality_severity ON data_quality(entity_type, severity, entity_id);

INSERT OR IGNORE INTO sync_meta(resource, last_modified) VALUES ('artists',          '2020-01-01T00:00:00Z');
INSERT OR IGNORE INTO sync_meta(resource, last_modified) VALUES ('concerts',         '2020-01-01T00:00:00Z');
INSERT OR IGNORE INTO sync_meta(resource, last_modified) VALUES ('salas-concierto',  '2020-01-01T00:00:00Z');

CREATE TABLE IF NOT EXISTS sync_runs (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'running',
    started_at TEXT NOT NULL,
    completed_at TEXT,
    salas_count INTEGER NOT NULL DEFAULT 0,
    artists_count INTEGER NOT NULL DEFAULT 0,
    concerts_count INTEGER NOT NULL DEFAULT 0,
    errors_count INTEGER NOT NULL DEFAULT 0,
    discrepancies_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TEXT NOT NULL
);

UPDATE sync_runs SET status = 'failed', error_message = 'Server restarted during run' WHERE status = 'running';

-- Enforce at most one running sync at a time at the DB level.
-- Placed AFTER the cleanup UPDATE above so any stale 'running' rows are already
-- cleared before we create the unique constraint.
CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_runs_single_running
    ON sync_runs(status) WHERE status = 'running';
