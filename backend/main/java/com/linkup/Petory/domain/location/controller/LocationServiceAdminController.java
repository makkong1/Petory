package com.linkup.Petory.domain.location.controller;

import com.linkup.Petory.domain.location.service.FacilitySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/location")
@RequiredArgsConstructor
public class LocationServiceAdminController {

    private final FacilitySyncService facilitySyncService;

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> syncFacilities() {
        FacilitySyncService.SyncResult result = facilitySyncService.syncFromPetDataApi();
        return ResponseEntity.ok(Map.of(
                "total", result.getTotal(),
                "saved", result.getSaved(),
                "duplicate", result.getDuplicate(),
                "skipped", result.getSkipped()
        ));
    }
}
