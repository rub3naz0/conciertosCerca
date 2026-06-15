package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.ManualCrudInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ManualCrudUseCase implements ManualCrudInputPort {

    // sourceUrl marker for all manually created entities (not a real URL — a sentinel)
    static final String MANUAL_SOURCE_URL = "manual";

    private final ConcertWritePort concertWritePort;
    private final SalaConciertoWritePort salaWritePort;
    private final ArtistWritePort artistWritePort;
    private final SalaConciertoRepositoryPort salaRepo;
    private final ArtistRepositoryPort artistRepo;
    private final ConcertRepositoryPort concertRepo;
    private final VenueGeocodingUseCase venueGeocoding;
    private final SyncMetadataWritePort syncMeta;
    private final DataQualityWritePort dataQualityWritePort;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public ManualCrudUseCase(
            ConcertWritePort concertWritePort,
            SalaConciertoWritePort salaWritePort,
            ArtistWritePort artistWritePort,
            SalaConciertoRepositoryPort salaRepo,
            ArtistRepositoryPort artistRepo,
            ConcertRepositoryPort concertRepo,
            VenueGeocodingUseCase venueGeocoding,
            SyncMetadataWritePort syncMeta,
            DataQualityWritePort dataQualityWritePort,
            TransactionTemplate txTemplate,
            Clock clock) {
        this.concertWritePort = concertWritePort;
        this.salaWritePort = salaWritePort;
        this.artistWritePort = artistWritePort;
        this.salaRepo = salaRepo;
        this.artistRepo = artistRepo;
        this.concertRepo = concertRepo;
        this.venueGeocoding = venueGeocoding;
        this.syncMeta = syncMeta;
        this.dataQualityWritePort = dataQualityWritePort;
        this.txTemplate = txTemplate;
        this.clock = clock;
    }

    @Override
    public void deleteConcert(String concertId) {
        int affected = txTemplate.execute(status -> {
            int rows = concertWritePort.markDeleted(concertId);
            if (rows > 0) {
                syncMeta.updateLastModified("concerts", Instant.now(clock));
            }
            return rows;
        });
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Concert not found: " + concertId);
        }
    }

    @Override
    public SalaConcierto createSala(CreateSalaCommand cmd) {
        validateRequired(cmd.name(), "name");
        validateRequired(cmd.address(), "address");
        validateRequired(cmd.city(), "city");
        validateRequired(cmd.province(), "province");
        CoordinateValidator.validate(cmd.lat(), cmd.lng());

        Instant now = Instant.now(clock);
        String id = ManualIdMinter.salaId(cmd.name(), cmd.province());

        if (salaRepo.findByIdIncludingBlocked(id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Sala already exists: " + id);
        }

        SalaConcierto sala = new SalaConcierto(
            id, cmd.name(), cmd.address(), cmd.city(), cmd.province(),
            cmd.lat(), cmd.lng(), cmd.imageUrl(), cmd.description(),
            MANUAL_SOURCE_URL, now
        );

        // geocoding calls external HTTP — keep it outside the transaction
        if (cmd.lat() == null && cmd.lng() == null) {
            sala = venueGeocoding.geocodeIfNeeded(sala, now);
        }

        SalaConcierto toPersist = sala;
        txTemplate.executeWithoutResult(status -> {
            salaWritePort.upsert(toPersist);
            syncMeta.updateLastModified("salas-concierto", now);
        });
        return sala;
    }

    @Override
    public Artist createArtist(CreateArtistCommand cmd) {
        validateRequired(cmd.name(), "name");

        Instant now = Instant.now(clock);
        String id = ManualIdMinter.artistId(cmd.name());

        if (artistRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Artist already exists: " + id);
        }

        Artist artist = new Artist(
            id, cmd.name(), cmd.genre(), cmd.imageUrl(), cmd.website(),
            MANUAL_SOURCE_URL, cmd.description(), now
        );

        txTemplate.executeWithoutResult(status -> {
            artistWritePort.upsert(artist);
            syncMeta.updateLastModified("artists", now);
        });
        return artist;
    }

    @Override
    public Concert createConcert(CreateConcertCommand cmd) {
        if (cmd.salaConciertoId() == null || cmd.salaConciertoId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "salaConciertoId is required");
        }
        if (cmd.artistIds() == null || cmd.artistIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artistIds must not be empty");
        }
        if (cmd.date() == null || cmd.date().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }

        // parse date — DateTimeParseException propagates as 400 via GlobalExceptionHandler
        LocalDate date = LocalDate.parse(cmd.date());

        // sala FK check
        salaRepo.findByIdIncludingBlocked(cmd.salaConciertoId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Sala not found: " + cmd.salaConciertoId()));

        // artist FK checks — collect all missing ids
        List<String> missing = new ArrayList<>();
        for (String artistId : cmd.artistIds()) {
            if (!artistRepo.existsById(artistId)) {
                missing.add(artistId);
            }
        }
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Artists not found: " + missing);
        }

        Instant now = Instant.now(clock);
        String id = ManualIdMinter.concertId(cmd.salaConciertoId(), date);

        // deleted concerts are not "active" — re-creating one revives it
        if (concertRepo.existsActiveById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Concert already exists: " + id);
        }

        Concert concert = new Concert(
            id, cmd.salaConciertoId(), cmd.artistIds(),
            date, cmd.time(), cmd.price(),
            MANUAL_SOURCE_URL, now
        );

        // upsert is multi-statement (concert row + junction rows) — must be atomic
        txTemplate.executeWithoutResult(status -> {
            concertWritePort.upsert(concert);
            syncMeta.updateLastModified("concerts", now);
        });
        return concert;
    }

    @Override
    public SalaConcierto updateSala(String id, UpdateSalaCommand cmd) {
        SalaConcierto stored = salaRepo.findByIdIncludingBlocked(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Sala not found: " + id));

        validateIdMatch(id, cmd.id());
        validateRequired(cmd.name(), "name");
        validateRequired(cmd.address(), "address");
        validateRequired(cmd.city(), "city");
        validateRequired(cmd.province(), "province");
        CoordinateValidator.validate(cmd.lat(), cmd.lng());

        Instant now = Instant.now(clock);

        SalaConcierto edited = new SalaConcierto(
            id, cmd.name(), cmd.address(), cmd.city(), cmd.province(),
            cmd.lat(), cmd.lng(), cmd.imageUrl(), cmd.description(),
            stored.sourceUrl(), now
        );

        // conditional geocoding — only when the address changed AND no coordinates were
        // supplied; mirrors createSala and happens OUTSIDE the transaction (external HTTP call)
        boolean addressChanged = !Objects.equals(stored.address(), edited.address());
        if (addressChanged && edited.lat() == null && edited.lng() == null) {
            edited = venueGeocoding.geocodeIfNeeded(edited, now);
        }

        SalaConcierto toPersist = edited;
        txTemplate.executeWithoutResult(status -> {
            salaWritePort.updateAll(toPersist);
            syncMeta.updateLastModified("salas-concierto", now);
            resolvePendingIfChanged("sala", id, "name", stored.name(), toPersist.name(), now);
            resolvePendingIfChanged("sala", id, "address", stored.address(), toPersist.address(), now);
            resolvePendingIfChanged("sala", id, "city", stored.city(), toPersist.city(), now);
            resolvePendingIfChanged("sala", id, "province", stored.province(), toPersist.province(), now);
            resolvePendingIfChanged("sala", id, "lat", stored.lat(), toPersist.lat(), now);
            resolvePendingIfChanged("sala", id, "lng", stored.lng(), toPersist.lng(), now);
            resolvePendingIfChanged("sala", id, "description", stored.description(), toPersist.description(), now);
            resolvePendingIfChanged("sala", id, "image_url", stored.imageUrl(), toPersist.imageUrl(), now);
        });
        return toPersist;
    }

    @Override
    public Artist updateArtist(String id, UpdateArtistCommand cmd) {
        Artist stored = artistRepo.findByIdIncludingBlocked(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Artist not found: " + id));

        validateIdMatch(id, cmd.id());
        validateRequired(cmd.name(), "name");

        Instant now = Instant.now(clock);

        Artist edited = new Artist(
            id, cmd.name(), cmd.genre(), cmd.imageUrl(), stored.website(),
            stored.sourceUrl(), cmd.description(), now
        );

        txTemplate.executeWithoutResult(status -> {
            artistWritePort.updateAll(edited);
            syncMeta.updateLastModified("artists", now);
            resolvePendingIfChanged("artist", id, "name", stored.name(), edited.name(), now);
            resolvePendingIfChanged("artist", id, "genre", stored.genre(), edited.genre(), now);
            resolvePendingIfChanged("artist", id, "description", stored.description(), edited.description(), now);
            resolvePendingIfChanged("artist", id, "image_url", stored.imageUrl(), edited.imageUrl(), now);
        });
        return edited;
    }

    @Override
    public Concert updateConcert(String id, UpdateConcertCommand cmd) {
        Concert stored = concertRepo.findByIdIncludingDeleted(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Concert not found: " + id));

        // soft-deleted concerts are not editable — the only resurrection guard
        // (ConcertWritePort.upsert forces deleted=0)
        // findByIdIncludingDeleted returns deleted rows too, so we must check explicitly.
        // The domain Concert record has no `deleted` flag — soft-delete state is detected
        // by re-checking active existence.
        if (!concertRepo.existsActiveById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Concert not found: " + id);
        }

        validateIdMatch(id, cmd.id());

        if (cmd.salaConciertoId() != null && !cmd.salaConciertoId().equals(stored.salaConciertoId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "salaConciertoId cannot be changed via edit");
        }
        if (cmd.artistIds() != null && !cmd.artistIds().equals(stored.artistIds())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "artistIds cannot be changed via edit");
        }

        Instant now = Instant.now(clock);

        LocalDate date = cmd.date() != null ? LocalDate.parse(cmd.date()) : stored.date();
        String time = cmd.time() != null ? cmd.time() : stored.time();
        String price = cmd.price() != null ? cmd.price() : stored.price();

        Concert edited = new Concert(
            id, stored.salaConciertoId(), stored.artistIds(),
            date, time, price, stored.sourceUrl(), now
        );

        txTemplate.executeWithoutResult(status -> {
            concertWritePort.upsert(edited);
            syncMeta.updateLastModified("concerts", now);
        });
        return edited;
    }

    @Override
    public SalaConcierto getSala(String id) {
        return salaRepo.findByIdIncludingBlocked(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Sala not found: " + id));
    }

    @Override
    public Artist getArtist(String id) {
        return artistRepo.findByIdIncludingBlocked(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Artist not found: " + id));
    }

    @Override
    public Concert getConcert(String id) {
        Concert concert = concertRepo.findByIdIncludingDeleted(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Concert not found: " + id));
        if (!concertRepo.existsActiveById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Concert not found: " + id);
        }
        return concert;
    }

    @Override
    public List<SalaConcierto> listSalas() {
        return salaRepo.findAllIncludingBlocked();
    }

    @Override
    public List<Artist> listArtists() {
        return artistRepo.findAllIncludingBlocked();
    }

    @Override
    public List<Concert> listConcerts() {
        return concertRepo.findAllIncludingBlocked();
    }

    private static void validateIdMatch(String pathId, String bodyId) {
        if (bodyId != null && !bodyId.equals(pathId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "id in body (" + bodyId + ") does not match id in path (" + pathId + ")");
        }
    }

    private void resolvePendingIfChanged(String entityType, String entityId, String field,
                                          Object oldValue, Object newValue, Instant now) {
        if (!Objects.equals(oldValue, newValue)) {
            String value = newValue == null ? null : String.valueOf(newValue);
            dataQualityWritePort.resolvePendingForField(entityType, entityId, field, value, now);
        }
    }

    private static void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                fieldName + " is required");
        }
    }
}
