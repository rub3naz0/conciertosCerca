package com.rubenazo.buscaConciertos.adminweb.adapters.in;

import com.rubenazo.buscaConciertos.adminweb.adapters.in.dto.SevereIssueDto;
import com.rubenazo.buscaConciertos.adminweb.application.AdminQualityUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/quality")
public class AdminQualityApi {

    public record FillRequest(String value) {}

    private final AdminQualityUseCase useCase;

    public AdminQualityApi(AdminQualityUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/severe")
    public ResponseEntity<List<SevereIssueDto>> listSevere() {
        return ResponseEntity.ok(
            useCase.listUnresolvedSevere().stream()
                .map(SevereIssueDto::from)
                .toList()
        );
    }

    @PostMapping("/{id}/fill")
    public ResponseEntity<Void> fill(@PathVariable Long id, @RequestBody FillRequest request) {
        useCase.fill(id, request.value());
        return ResponseEntity.ok().build();
    }
}
