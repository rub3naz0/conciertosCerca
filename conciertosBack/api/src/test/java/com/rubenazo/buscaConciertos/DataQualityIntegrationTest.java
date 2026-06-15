package com.rubenazo.buscaConciertos;

import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataPort;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class DataQualityIntegrationTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataQualityWritePort dataQualityWritePort;

    @Autowired
    private DataQualityRepositoryPort dataQualityRepositoryPort;

    @Autowired
    private ArtistWritePort artistWritePort;

    @Autowired
    private SalaConciertoWritePort salaConciertoWritePort;

    @Autowired
    private SyncMetadataPort syncMetadataPort;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.execute("DELETE FROM data_quality");
        jdbcTemplate.execute("DELETE FROM concert_artists");
        jdbcTemplate.execute("DELETE FROM concerts");
        jdbcTemplate.execute("DELETE FROM artists");
        jdbcTemplate.execute("DELETE FROM salas_concierto");
        jdbcTemplate.execute("UPDATE sync_meta SET last_modified = '2020-01-01T00:00:00Z'");
    }

    // admin mutations need the CSRF hardening header (see CsrfHardeningFilter)
    private static MockHttpServletRequestBuilder adminPost(String url) {
        return post(url).header("X-Requested-With", "XMLHttpRequest");
    }

    // --- Scenario: list all issues returns 200 ---

    @Test
    void listIssues_returns200AndEmptyListWhenNoIssues() throws Exception {
        mockMvc.perform(get("/api/admin/quality"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // --- Scenario: detect → list → approve → sync bump (full flow for artist) ---

    @Test
    void approveArtistSuggestion_writesFieldAndBumpsSyncMeta() throws Exception {
        // Insert a real artist with null genre
        Artist artist = new Artist("a1", "Vetusta Morla", null,
                null, null, null, null, BASE_TIME);
        artistWritePort.upsert(artist);

        // Simulate detection: insert a data_quality row manually (as SyncUseCase would)
        DataQuality dq = new DataQuality(null, "artist", "a1", "genre", "auto_found",
                "non_severe", "Indie Rock", "https://tavily.com", 0.85, BASE_TIME);
        dataQualityWritePort.saveAll(List.of(dq));

        // Verify the row is in the DB
        List<DataQuality> issues = dataQualityRepositoryPort.findByStatus("auto_found");
        assertThat(issues).hasSize(1);
        Long issueId = issues.get(0).id();

        // Record sync_meta before approval
        Instant syncBefore = syncMetadataPort.getLastModified("artists");

        // Call approve endpoint
        mockMvc.perform(adminPost("/api/admin/quality/" + issueId + "/approve"))
            .andExpect(status().isOk());

        // Verify: data_quality row is now 'approved'
        List<DataQuality> afterApprove = dataQualityRepositoryPort.findByStatus("approved");
        assertThat(afterApprove).hasSize(1);
        assertThat(afterApprove.get(0).status()).isEqualTo("approved");

        // Verify: artist genre was written back
        String genre = jdbcTemplate.queryForObject(
            "SELECT genre FROM artists WHERE id = 'a1'", String.class);
        assertThat(genre).isEqualTo("Indie Rock");

        // Verify: sync_meta.last_modified was bumped
        Instant syncAfter = syncMetadataPort.getLastModified("artists");
        assertThat(syncAfter).isAfter(syncBefore);
    }

    // --- Scenario: detect → approve for sala ---

    @Test
    void approveSalaSuggestion_writesFieldAndBumpsSalasSyncMeta() throws Exception {
        // Insert a real sala with null image_url
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla, 113",
                "Barcelona", "Barcelona", 41.374, 2.168,
                null, null, null, BASE_TIME);
        salaConciertoWritePort.upsert(sala);

        // Insert data_quality row for image_url
        DataQuality dq = new DataQuality(null, "sala", "s1", "image_url", "auto_found",
                "non_severe", "https://img.sala-apolo.com/logo.jpg", "https://tavily.com", 0.88, BASE_TIME);
        dataQualityWritePort.saveAll(List.of(dq));

        List<DataQuality> issues = dataQualityRepositoryPort.findByStatus("auto_found");
        Long issueId = issues.get(0).id();

        Instant syncBefore = syncMetadataPort.getLastModified("salas-concierto");

        mockMvc.perform(adminPost("/api/admin/quality/" + issueId + "/approve"))
            .andExpect(status().isOk());

        // Sala image_url was written back
        String imageUrl = jdbcTemplate.queryForObject(
            "SELECT image_url FROM salas_concierto WHERE id = 's1'", String.class);
        assertThat(imageUrl).isEqualTo("https://img.sala-apolo.com/logo.jpg");

        // sync_meta bumped
        Instant syncAfter = syncMetadataPort.getLastModified("salas-concierto");
        assertThat(syncAfter).isAfter(syncBefore);
    }

    // --- Scenario: reject — no entity write, no sync bump ---

    @Test
    void rejectSuggestion_setsStatusRejectedAndDoesNotBumpSync() throws Exception {
        Artist artist = new Artist("a2", "Sidonie", null,
                null, null, null, null, BASE_TIME);
        artistWritePort.upsert(artist);

        DataQuality dq = new DataQuality(null, "artist", "a2", "genre", "auto_found",
                "non_severe", "Pop Rock", "https://tavily.com", null, BASE_TIME);
        dataQualityWritePort.saveAll(List.of(dq));

        List<DataQuality> issues = dataQualityRepositoryPort.findByStatus("auto_found");
        Long issueId = issues.get(0).id();

        Instant syncBefore = syncMetadataPort.getLastModified("artists");

        mockMvc.perform(adminPost("/api/admin/quality/" + issueId + "/reject"))
            .andExpect(status().isOk());

        List<DataQuality> rejected = dataQualityRepositoryPort.findByStatus("rejected");
        assertThat(rejected).hasSize(1);

        // artist genre was NOT written
        String genre = jdbcTemplate.queryForObject(
            "SELECT genre FROM artists WHERE id = 'a2'", String.class);
        assertThat(genre).isNull();

        // sync_meta NOT bumped
        Instant syncAfter = syncMetadataPort.getLastModified("artists");
        assertThat(syncAfter).isEqualTo(syncBefore);
    }

    // --- Scenario: approve non-existent row → 404 ---

    @Test
    void approve_returns404ForNonExistentId() throws Exception {
        mockMvc.perform(adminPost("/api/admin/quality/99999/approve"))
            .andExpect(status().isNotFound());
    }

    // --- Scenario: approve already approved row → 409 ---

    @Test
    void approve_returns409WhenAlreadyApproved() throws Exception {
        Artist artist = new Artist("a3", "M Clan", null,
                null, null, null, null, BASE_TIME);
        artistWritePort.upsert(artist);

        DataQuality dq = new DataQuality(null, "artist", "a3", "genre", "approved",
                "non_severe", "Rock", "https://tavily.com", null, BASE_TIME);
        dataQualityWritePort.saveAll(List.of(dq));

        List<DataQuality> issues = dataQualityRepositoryPort.findByStatus("approved");
        Long issueId = issues.get(0).id();

        mockMvc.perform(adminPost("/api/admin/quality/" + issueId + "/approve"))
            .andExpect(status().isConflict());
    }

    // --- Scenario: filter by status ---

    @Test
    void listIssues_filtersByStatus() throws Exception {
        DataQuality dq1 = new DataQuality(null, "artist", "a1", "genre", "auto_found",
                "non_severe", "Rock", "https://tavily.com", 0.88, BASE_TIME);
        DataQuality dq2 = new DataQuality(null, "sala", "s1", "phone", "missing",
                "non_severe", null, null, null, BASE_TIME);
        dataQualityWritePort.saveAll(List.of(dq1, dq2));

        mockMvc.perform(get("/api/admin/quality?status=auto_found"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("auto_found"));
    }

    // --- Scenario: unknown status → 400 ---

    @Test
    void listIssues_returns400ForUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/admin/quality?status=invalid_value"))
            .andExpect(status().isBadRequest());
    }

    // --- Scenario: approve auto_approved row → 409 ---

    @Test
    void approve_returns409WhenAutoApproved() throws Exception {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "C/ Nou de la Rambla, 113",
                "Barcelona", "Barcelona", 41.374, 2.168,
                null, null, null, BASE_TIME);
        salaConciertoWritePort.upsert(sala);

        // Insert directly with auto_approved status
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "sala", "s1", "description", "auto_approved", "non_severe", "Great venue", "https://tavily.com", 0.92, BASE_TIME.toString()
        );

        Long issueId = jdbcTemplate.queryForObject("SELECT id FROM data_quality WHERE entity_id='s1'", Long.class);

        mockMvc.perform(adminPost("/api/admin/quality/" + issueId + "/approve"))
            .andExpect(status().isConflict());
    }

    // --- Phase 4: reImport_doesNotOverwrite_autoApproved ---

    @Test
    void reImport_doesNotOverwrite_autoApproved() throws Exception {
        // Insert a row with status=auto_approved
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "sala", "s5", "description", "auto_approved", "non_severe", "Awesome venue", "https://tavily.com", 0.92, BASE_TIME.toString()
        );

        // Re-import: saveAll with status=missing for same row
        DataQuality reimport = new DataQuality(null, "sala", "s5", "description", "missing", "non_severe", null, null, null, BASE_TIME);
        dataQualityWritePort.saveAll(List.of(reimport));

        // Row must still be auto_approved
        List<DataQuality> autoApproved = dataQualityRepositoryPort.findByStatus("auto_approved");
        assertThat(autoApproved).hasSize(1);
        assertThat(autoApproved.get(0).status()).isEqualTo("auto_approved");
        assertThat(autoApproved.get(0).suggested()).isEqualTo("Awesome venue");

        // No duplicate row
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM data_quality WHERE entity_id='s5' AND field='description'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // --- Batch approve endpoint ---

    @Test
    void approveAll_returns200WithCount() throws Exception {
        SalaConcierto sala = new SalaConcierto("s2", "Razzmatazz", "C/ dels Almogàvers, 122",
                "Barcelona", "Barcelona", 41.399, 2.196,
                null, null, null, BASE_TIME);
        salaConciertoWritePort.upsert(sala);

        // Insert auto_found rows with different scores
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "sala", "s2", "description", "auto_found", "non_severe", "Great club", "https://src.com", 0.92, BASE_TIME.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "sala", "s2", "image_url", "auto_found", "non_severe", "https://img.com/logo.jpg", "https://src.com", 0.75, BASE_TIME.toString()
        );

        // approve-all with minScore=0.8 → only description qualifies
        mockMvc.perform(adminPost("/api/admin/quality/approve-all?minScore=0.8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approved").value(1));
    }

    // --- List score filter ---

    @Test
    void listIssues_minScoreFilter_excludesNullAndBelowThreshold() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "artist", "a1", "website", "auto_found", "non_severe", "https://band.com", "https://src.com", 0.91, BASE_TIME.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, score, updated_at) VALUES (?,?,?,?,?,?,?)",
            "artist", "a2", "genre", "auto_found", "severe", 0.75, BASE_TIME.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a3", "description", "missing", "non_severe", BASE_TIME.toString()
        );

        mockMvc.perform(get("/api/admin/quality?minScore=0.8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].score").value(0.91));
    }

    // --- Scenario: approve-all without minScore returns 400 ---

    @Test
    void approveAll_missingMinScore_returns400() throws Exception {
        mockMvc.perform(adminPost("/api/admin/quality/approve-all"))
            .andExpect(status().isBadRequest());
    }

    // --- listIssues with auto_approved status filter ---

    @Test
    void listIssues_statusAutoApproved_isValidFilter() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "sala", "s1", "description", "auto_approved", "non_severe", "Great venue", "https://src.com", 0.92, BASE_TIME.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "genre", "missing", "severe", BASE_TIME.toString()
        );

        mockMvc.perform(get("/api/admin/quality?status=auto_approved"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("auto_approved"));
    }
}
