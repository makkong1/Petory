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
/** 관리자용 케어 요청 목록 조회·상태 변경·삭제·복구 API. [ADMIN, MASTER] */
public class AdminCareRequestController {

    private final AdminCareAndMeetupFacade facade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<Page<CareRequestDTO>> listCareRequests(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "deleted", required = false) Boolean deleted,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(facade.getCareRequests(status, deleted, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CareRequestDTO> getCareRequest(@PathVariable("id") Long id) {
        return ResponseEntity.ok(facade.getCareRequest(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CareRequestDTO> updateStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") String status) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(facade.updateCareStatus(id, status, adminIdx));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteCareRequest(@PathVariable("id") Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        facade.deleteCareRequest(id, adminIdx);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<CareRequestDTO> restoreCareRequest(@PathVariable("id") Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(facade.restoreCareRequest(id, adminIdx));
    }
}
