package com.linkup.Petory.domain.admin.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.service.CareRequestService;

import lombok.RequiredArgsConstructor;

/**
 * 케어서비스 관리 컨트롤러 (관리자용)
 * - ADMIN과 MASTER 모두 접근 가능
 * - 케어 요청 목록 조회, 상태 변경, 삭제/복구
 */
@RestController
@RequestMapping("/api/admin/care-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminCareRequestController {

    private final CareRequestService careRequestService;

    /**
     * 현재 로그인한 사용자의 ID 추출
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("인증되지 않은 사용자입니다.");
        }
        String userIdString = authentication.getName();
        try {
            return Long.parseLong(userIdString);
        } catch (NumberFormatException e) {
            throw new RuntimeException("유효하지 않은 사용자 ID입니다.");
        }
    }

    /**
     * 케어 요청 목록 조회 (필터링 지원)
     */
    @GetMapping
    public ResponseEntity<List<CareRequestDTO>> listCareRequests(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "deleted", required = false) Boolean deleted,
            @RequestParam(value = "q", required = false) String q) {
        
        List<CareRequestDTO> all = careRequestService.getAllCareRequests(status, location);
        
        // 삭제 여부 필터 (서비스에서 삭제된 것 제외하므로, deleted=true일 때만 추가 조회 필요)
        // TODO: 서비스에 관리자용 전체 조회 메서드 추가 필요
        if (Boolean.TRUE.equals(deleted)) {
            // 삭제된 것도 포함하려면 서비스 메서드 추가 필요
            // 현재는 삭제되지 않은 것만 반환
        }
        
        // 검색어 필터
        if (q != null && !q.isBlank()) {
            String keyword = q.toLowerCase();
            all = all.stream()
                    .filter(r -> (r.getTitle() != null && r.getTitle().toLowerCase().contains(keyword))
                            || (r.getDescription() != null && r.getDescription().toLowerCase().contains(keyword))
                            || (r.getUsername() != null && r.getUsername().toLowerCase().contains(keyword)))
                    .collect(Collectors.toList());
        }
        
        return ResponseEntity.ok(all);
    }

    /**
     * 케어 요청 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<CareRequestDTO> getCareRequest(@PathVariable Long id) {
        return ResponseEntity.ok(careRequestService.getCareRequest(id));
    }

    /**
     * 케어 요청 상태 변경 (관리자는 권한 검증 우회)
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<CareRequestDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(careRequestService.updateStatus(id, status, currentUserId));
    }

    /**
     * 케어 요청 삭제 (소프트 삭제, 관리자는 권한 검증 우회)
     */
    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteCareRequest(@PathVariable Long id) {
        Long currentUserId = getCurrentUserId();
        careRequestService.deleteCareRequest(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 케어 요청 복구
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<CareRequestDTO> restoreCareRequest(@PathVariable Long id) {
        // TODO: 서비스에 복구 메서드 추가 필요
        throw new UnsupportedOperationException("복구 기능은 아직 구현되지 않았습니다.");
    }
}

