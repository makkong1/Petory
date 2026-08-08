package com.linkup.Petory.domain.care.controller;

import com.linkup.Petory.global.config.NearbySearchPolicy;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.dto.CareRequestListView;
import com.linkup.Petory.domain.care.dto.CareRequestPageResponseDTO;
import com.linkup.Petory.domain.care.service.CareRequestService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 펫케어 요청 생성·조회·지원·진행·완료 API.
 */
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
    public ResponseEntity<List<CareRequestListView>> getNearby(
            @RequestParam(value = "lat") double lat,
            @RequestParam(value = "lng") double lng,
            @RequestParam(value = "radius", defaultValue = "5.0") double radius,
            @RequestParam(value = "limit", required = false) Integer limit) {
        // [지도 반경검색 통일] 상한을 NearbySearchPolicy 한 곳에서 정한다.
        // 예전엔 프론트 ZOOM_LIMIT_TABLE(줌 레벨 기준)이 limit 을 보냈는데,
        // 쿼리가 읽을 행 수를 정하는 건 줌이 아니라 반경이라 둘이 어긋났다.
        return ResponseEntity.ok(careRequestService.getNearby(
                lat, lng, radius, NearbySearchPolicy.clampResultLimit(limit, radius)));
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
        // 프론트는 userId 미전달(예: CareCreateModal) — 인증 사용자 PK로 채워야 한다. null 이면 JPA findById(null) 로 500 발생.
        dto.setUserId(getCurrentUserId());
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

    /**
     * 이행 완료 확인. 요청자와 제공자가 각자 호출하고, 양쪽이 모두 확인해야 COMPLETED 로 넘어가며 정산된다.
     * 한쪽만 확인한 상태에서는 상대 확인을 기다린다(응답의 상태는 여전히 IN_PROGRESS).
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareRequestDTO> confirmCompletion(@PathVariable("id") Long id) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(careRequestService.confirmCompletion(id, currentUserId));
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
