package com.linkup.Petory.domain.place.controller;

import com.linkup.Petory.domain.place.dto.BatchIngestRequest;
import com.linkup.Petory.domain.place.service.PlaceCandidateIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/place-candidates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class PlaceCandidateIngestController {

    private final PlaceCandidateIngestService ingestService;

    @PostMapping("/batch-ingest")
    public ResponseEntity<Map<String, Object>> batchIngest(@RequestBody BatchIngestRequest request) {
        int saved = ingestService.ingest(request);
        return ResponseEntity.ok(Map.of("saved", saved, "total", request.getCandidates().size()));
    }
}
