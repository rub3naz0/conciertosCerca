package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.adapters.in.dto.ArtistDto;
import com.rubenazo.buscaConciertos.adapters.in.dto.ConcertDto;
import com.rubenazo.buscaConciertos.adapters.in.dto.CreateArtistRequest;
import com.rubenazo.buscaConciertos.adapters.in.dto.CreateConcertRequest;
import com.rubenazo.buscaConciertos.adapters.in.dto.CreateSalaRequest;
import com.rubenazo.buscaConciertos.adapters.in.dto.SalaConciertoDto;
import com.rubenazo.buscaConciertos.adapters.in.dto.UpdateArtistRequest;
import com.rubenazo.buscaConciertos.adapters.in.dto.UpdateConcertRequest;
import com.rubenazo.buscaConciertos.adapters.in.dto.UpdateSalaRequest;
import com.rubenazo.buscaConciertos.application.CreateArtistCommand;
import com.rubenazo.buscaConciertos.application.CreateConcertCommand;
import com.rubenazo.buscaConciertos.application.CreateSalaCommand;
import com.rubenazo.buscaConciertos.application.UpdateArtistCommand;
import com.rubenazo.buscaConciertos.application.UpdateConcertCommand;
import com.rubenazo.buscaConciertos.application.UpdateSalaCommand;
import com.rubenazo.buscaConciertos.application.ports.in.ManualCrudInputPort;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Admin — Manual CRUD")
public class AdminCrudApi {

    private final ManualCrudInputPort manualCrudInputPort;

    public AdminCrudApi(ManualCrudInputPort manualCrudInputPort) {
        this.manualCrudInputPort = manualCrudInputPort;
    }

    @DeleteMapping("/api/admin/concerts/{id}")
    public ResponseEntity<Void> deleteConcert(@PathVariable String id) {
        manualCrudInputPort.deleteConcert(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/salas")
    public ResponseEntity<SalaConciertoDto> createSala(@RequestBody CreateSalaRequest req) {
        var cmd = new CreateSalaCommand(
            req.name(), req.address(), req.city(), req.province(),
            req.lat(), req.lng(), req.imageUrl(), req.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(SalaConciertoDto.from(manualCrudInputPort.createSala(cmd)));
    }

    @PostMapping("/api/admin/artists")
    public ResponseEntity<ArtistDto> createArtist(@RequestBody CreateArtistRequest req) {
        var cmd = new CreateArtistCommand(
            req.name(), req.genre(), req.imageUrl(), req.website(), req.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ArtistDto.from(manualCrudInputPort.createArtist(cmd)));
    }

    @PostMapping("/api/admin/concerts")
    public ResponseEntity<ConcertDto> createConcert(@RequestBody CreateConcertRequest req) {
        var cmd = new CreateConcertCommand(
            req.salaConciertoId(), req.artistIds(), req.date(), req.time(), req.price()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ConcertDto.from(manualCrudInputPort.createConcert(cmd)));
    }

    @PutMapping("/api/admin/salas/{id}")
    public ResponseEntity<SalaConciertoDto> updateSala(@PathVariable String id, @RequestBody UpdateSalaRequest req) {
        var cmd = new UpdateSalaCommand(
            req.name(), req.address(), req.city(), req.province(),
            req.lat(), req.lng(), req.imageUrl(), req.description(), req.id()
        );
        return ResponseEntity.ok(SalaConciertoDto.from(manualCrudInputPort.updateSala(id, cmd)));
    }

    @PutMapping("/api/admin/artists/{id}")
    public ResponseEntity<ArtistDto> updateArtist(@PathVariable String id, @RequestBody UpdateArtistRequest req) {
        var cmd = new UpdateArtistCommand(
            req.name(), req.genre(), req.description(), req.imageUrl(), req.id()
        );
        return ResponseEntity.ok(ArtistDto.from(manualCrudInputPort.updateArtist(id, cmd)));
    }

    @PutMapping("/api/admin/concerts/{id}")
    public ResponseEntity<ConcertDto> updateConcert(@PathVariable String id, @RequestBody UpdateConcertRequest req) {
        var cmd = new UpdateConcertCommand(
            req.date(), req.time(), req.price(), req.id(), req.salaConciertoId(), req.artistIds()
        );
        return ResponseEntity.ok(ConcertDto.from(manualCrudInputPort.updateConcert(id, cmd)));
    }

    @GetMapping("/api/admin/salas/{id}")
    public ResponseEntity<SalaConciertoDto> getSala(@PathVariable String id) {
        return ResponseEntity.ok(SalaConciertoDto.from(manualCrudInputPort.getSala(id)));
    }

    @GetMapping("/api/admin/artists/{id}")
    public ResponseEntity<ArtistDto> getArtist(@PathVariable String id) {
        return ResponseEntity.ok(ArtistDto.from(manualCrudInputPort.getArtist(id)));
    }

    @GetMapping("/api/admin/concerts/{id}")
    public ResponseEntity<ConcertDto> getConcert(@PathVariable String id) {
        return ResponseEntity.ok(ConcertDto.from(manualCrudInputPort.getConcert(id)));
    }

    @GetMapping("/api/admin/salas")
    public ResponseEntity<List<SalaConciertoDto>> listSalas() {
        return ResponseEntity.ok(manualCrudInputPort.listSalas().stream().map(SalaConciertoDto::from).toList());
    }

    @GetMapping("/api/admin/artists")
    public ResponseEntity<List<ArtistDto>> listArtists() {
        return ResponseEntity.ok(manualCrudInputPort.listArtists().stream().map(ArtistDto::from).toList());
    }

    @GetMapping("/api/admin/concerts")
    public ResponseEntity<List<ConcertDto>> listConcerts() {
        return ResponseEntity.ok(manualCrudInputPort.listConcerts().stream().map(ConcertDto::from).toList());
    }
}
