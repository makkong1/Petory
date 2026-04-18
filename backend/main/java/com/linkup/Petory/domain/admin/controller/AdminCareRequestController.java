package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminCareAndMeetupFacade;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/care-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminCareRequestController {

    private final AdminCareAndMeetupFacade facade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<Page<CareRequestDTO>> listCareRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(facade.getCareRequests(status, deleted, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CareRequestDTO> getCareRequest(@PathVariable Long id) {
        return ResponseEntity.ok(facade.getCareRequest(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CareRequestDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(facade.updateCareStatus(id, status, adminIdx));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteCareRequest(@PathVariable Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        facade.deleteCareRequest(id, adminIdx);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<CareRequestDTO> restoreCareRequest(@PathVariable Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(facade.restoreCareRequest(id, adminIdx));
    }
}
