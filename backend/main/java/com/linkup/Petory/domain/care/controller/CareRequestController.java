package com.linkup.Petory.domain.care.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.service.CareRequestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        log.info("=== [펫케어 전체조회] API 호출 시작 - status: {}, location: {} ===", status, location);
        
        List<CareRequestDTO> result = careRequestService.getAllCareRequests(status, location);
        
        long endTime = System.currentTimeMillis();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long executionTime = endTime - startTime;
        long memoryUsed = memoryAfter - memoryBefore;
        
        log.info("=== [펫케어 전체조회] 완료 ===");
        log.info("  - 실행 시간: {}ms ({}초)", executionTime, executionTime / 1000.0);
        log.info("  - 메모리 사용량: {}MB ({}KB)", memoryUsed / (1024 * 1024), memoryUsed / 1024);
        log.info("  - 조회된 데이터 수: {}개", result.size());
        log.info("  - 현재 메모리 상태 - Total: {}MB, Free: {}MB, Used: {}MB",
                runtime.totalMemory() / (1024 * 1024),
                runtime.freeMemory() / (1024 * 1024),
                (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        
        return ResponseEntity.ok(result);
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
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        log.info("=== [펫케어 검색조회] API 호출 시작 - keyword: {} ===", keyword);
        
        List<CareRequestDTO> result = careRequestService.searchCareRequests(keyword);
        
        long endTime = System.currentTimeMillis();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long executionTime = endTime - startTime;
        long memoryUsed = memoryAfter - memoryBefore;
        
        log.info("=== [펫케어 검색조회] 완료 ===");
        log.info("  - 실행 시간: {}ms ({}초)", executionTime, executionTime / 1000.0);
        log.info("  - 메모리 사용량: {}MB ({}KB)", memoryUsed / (1024 * 1024), memoryUsed / 1024);
        log.info("  - 조회된 데이터 수: {}개", result.size());
        log.info("  - 현재 메모리 상태 - Total: {}MB, Free: {}MB, Used: {}MB",
                runtime.totalMemory() / (1024 * 1024),
                runtime.freeMemory() / (1024 * 1024),
                (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        
        return ResponseEntity.ok(result);
    }
}
