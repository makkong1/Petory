package com.linkup.Petory.domain.place.controller;

import com.linkup.Petory.domain.place.dto.*;
import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.service.PlaceCandidateAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/place-candidates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class PlaceCandidateAdminController {

    private final PlaceCandidateAdminService service;

    @GetMapping
    public ResponseEntity<Page<PlaceCandidateDto>> list(
        @RequestParam(defaultValue = "NEEDS_REVIEW") CandidateDecisionStatus status,
        @PageableDefault(size = 20, sort = "confidenceScore", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listByStatus(status, pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PlaceCandidateDto> approve(
        @PathVariable Long id,
        @RequestBody(required = false) CandidateApproveRequest req,
        Authentication auth
    ) {
        if (req == null) req = new CandidateApproveRequest();
        return ResponseEntity.ok(service.approve(id, req, auth.getName()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PlaceCandidateDto> reject(
        @PathVariable Long id,
        @RequestBody CandidateRejectRequest req,
        Authentication auth
    ) {
        return ResponseEntity.ok(service.reject(id, req, auth.getName()));
    }
}
