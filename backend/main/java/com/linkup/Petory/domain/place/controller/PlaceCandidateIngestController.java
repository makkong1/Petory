package com.linkup.Petory.domain.place.controller;

import com.linkup.Petory.domain.place.dto.BatchIngestRequest;
import com.linkup.Petory.domain.place.service.PlaceCandidateIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/place-candidates")
@RequiredArgsConstructor
public class PlaceCandidateIngestController {

    private final PlaceCandidateIngestService ingestService;

    @Value("${app.location.ingest.internal-key:}")
    private String internalKey;

    @PostMapping("/batch-ingest")
    public ResponseEntity<Map<String, Object>> batchIngest(
        @RequestHeader(value = "X-Ingest-Key", required = false) String key,
        @RequestBody BatchIngestRequest request
    ) {
        if (!internalKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "X-Ingest-Key 불일치"));
        }
        int saved = ingestService.ingest(request);
        return ResponseEntity.ok(Map.of("saved", saved, "total", request.getCandidates().size()));
    }
}
