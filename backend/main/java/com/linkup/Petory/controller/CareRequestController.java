package com.linkup.Petory.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.dto.CareRequestDTO;
import com.linkup.Petory.service.CareRequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/care-requests")
@RequiredArgsConstructor
public class CareRequestController {

    private final CareRequestService careRequestService;

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
        return ResponseEntity.ok(careRequestService.updateCareRequest(id, dto));
    }

    // 케어 요청 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCareRequest(@PathVariable Long id) {
        careRequestService.deleteCareRequest(id);
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
        return ResponseEntity.ok(careRequestService.updateStatus(id, status));
    }
}
