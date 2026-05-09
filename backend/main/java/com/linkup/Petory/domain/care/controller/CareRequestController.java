package com.linkup.Petory.domain.care.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.dto.CareRequestPageResponseDTO;
import com.linkup.Petory.domain.care.service.CareRequestService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/care-requests")
@RequiredArgsConstructor
public class CareRequestController {

    private final CareRequestService careRequestService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    private Long getCurrentUserId() {
        return authenticatedUserIdResolver.requireCurrentUserIdx();
    }

    // 반경 기반 근처 케어 요청 조회 (지도 표출용)
    @GetMapping("/nearby")
    public ResponseEntity<List<CareRequestDTO>> getNearby(
            @RequestParam(value = "lat") double lat,
            @RequestParam(value = "lng") double lng,
            @RequestParam(value = "radius", defaultValue = "5.0") double radius,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return ResponseEntity.ok(careRequestService.getNearby(lat, lng, radius, limit));
    }

    // 전체 케어 요청 조회 (페이징 지원)
    @GetMapping
    public ResponseEntity<CareRequestPageResponseDTO> getAllCareRequests(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(careRequestService.getCareRequestsWithPaging(status, location, page, size));
    }

    // 단일 케어 요청 조회
    @GetMapping("/{id}")
    public ResponseEntity<CareRequestDTO> getCareRequest(@PathVariable("id") Long id) {
        return ResponseEntity.ok(careRequestService.getCareRequest(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareRequestDTO> createCareRequest(@Valid @RequestBody CareRequestDTO dto) {
        return ResponseEntity.ok(careRequestService.createCareRequest(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareRequestDTO> updateCareRequest(@PathVariable("id") Long id, @RequestBody CareRequestDTO dto) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(careRequestService.updateCareRequest(id, dto, currentUserId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteCareRequest(@PathVariable("id") Long id) {
        Long currentUserId = getCurrentUserId();
        careRequestService.deleteCareRequest(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CareRequestDTO>> getMyCareRequests() {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(careRequestService.getMyCareRequests(currentUserId));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareRequestDTO> updateStatus(@PathVariable("id") Long id,
            @RequestParam(value = "status") String status) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(careRequestService.updateStatus(id, status, currentUserId));
    }

    // 케어 요청 검색 (페이징 지원)
    @GetMapping("/search")
    public ResponseEntity<CareRequestPageResponseDTO> searchCareRequests(
            @RequestParam(value = "keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(careRequestService.searchCareRequestsWithPaging(keyword, page, size));
    }
}
