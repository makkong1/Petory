package com.linkup.Petory.domain.care.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.service.CareRequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/care-requests")
@RequiredArgsConstructor
public class CareRequestController {

    private final CareRequestService careRequestService;

    /**
     * 현재 로그인한 사용자의 ID 추출
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("인증되지 않은 사용자입니다.");
        }
        // UserDetails의 username이 실제로는 userId (id 필드)
        String userIdString = authentication.getName();
        try {
            return Long.parseLong(userIdString);
        } catch (NumberFormatException e) {
            throw new RuntimeException("유효하지 않은 사용자 ID입니다.");
        }
    }

    // 전체 케어 요청 조회
    @GetMapping
    public ResponseEntity<List<CareRequestDTO>> getAllCareRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location) {
        System.out.println("=== API 호출됨: GET /api/care-requests ===");
        return ResponseEntity.ok(careRequestService.getAllCareRequests(status, location));
    }

    // 단일 케어 요청 조회
    @GetMapping("/{id}")
    public ResponseEntity<CareRequestDTO> getCareRequest(@PathVariable Long id) {
        return ResponseEntity.ok(careRequestService.getCareRequest(id));
    }

    // 케어 요청 생성
    @PostMapping
    public ResponseEntity<CareRequestDTO> createCareRequest(@RequestBody CareRequestDTO dto) {
        System.out.println("=== 케어 요청 생성: " + dto.getTitle() + " ===");
        return ResponseEntity.ok(careRequestService.createCareRequest(dto));
    }

    // 케어 요청 수정
    @PutMapping("/{id}")
    public ResponseEntity<CareRequestDTO> updateCareRequest(@PathVariable Long id, @RequestBody CareRequestDTO dto) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(careRequestService.updateCareRequest(id, dto, currentUserId));
    }

    // 케어 요청 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCareRequest(@PathVariable Long id) {
        Long currentUserId = getCurrentUserId();
        careRequestService.deleteCareRequest(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // 내 케어 요청 조회
    @GetMapping("/my-requests")
    public ResponseEntity<List<CareRequestDTO>> getMyCareRequests(@RequestParam Long userId) {
        return ResponseEntity.ok(careRequestService.getMyCareRequests(userId));
    }

    // 케어 요청 상태 변경
    @PatchMapping("/{id}/status")
    public ResponseEntity<CareRequestDTO> updateStatus(@PathVariable Long id, @RequestParam String status) {
        System.out.println("=== 상태 변경: " + id + " -> " + status + " ===");
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(careRequestService.updateStatus(id, status, currentUserId));
    }

    // 케어 요청 검색
    @GetMapping("/search")
    public ResponseEntity<List<CareRequestDTO>> searchCareRequests(@RequestParam String keyword) {
        return ResponseEntity.ok(careRequestService.searchCareRequests(keyword));
    }
}
