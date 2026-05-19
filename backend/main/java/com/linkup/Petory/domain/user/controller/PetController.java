package com.linkup.Petory.domain.user.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.user.dto.PetDTO;
import com.linkup.Petory.domain.user.exception.UnauthenticatedException;
import com.linkup.Petory.domain.user.service.PetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애완동물 관리 컨트롤러
 * - 모든 인증된 사용자가 자신의 펫을 관리할 수 있음
 */
@Slf4j
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    /**
     * 현재 로그인한 사용자의 ID 추출
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthenticatedException();
        }
        return authentication.getName();
    }

    /**
     * 자신의 펫 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<PetDTO>> getMyPets() {
        String userId = getCurrentUserId();
        List<PetDTO> pets = petService.getPetsByUserId(userId);
        return ResponseEntity.ok(pets);
    }

    /**
     * 펫 상세 조회
     */
    @GetMapping("/{petIdx}")
    public ResponseEntity<PetDTO> getPet(@PathVariable("petIdx") Long petIdx) {
        String userId = getCurrentUserId();
        PetDTO pet = petService.getPet(petIdx, userId);
        return ResponseEntity.ok(pet);
    }

    /**
     * 펫 생성
     */
    @PostMapping
    public ResponseEntity<PetDTO> createPet(@Valid @RequestBody PetDTO dto) {
        String userId = getCurrentUserId();
        PetDTO created = petService.createPet(userId, dto);
        return ResponseEntity.ok(created);
    }

    /**
     * 펫 수정
     */
    @PutMapping("/{petIdx}")
    public ResponseEntity<PetDTO> updatePet(@PathVariable("petIdx") Long petIdx, @RequestBody PetDTO dto) {
        String userId = getCurrentUserId();
        PetDTO updated = petService.updatePet(petIdx, userId, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * 펫 삭제 (소프트 삭제)
     */
    @DeleteMapping("/{petIdx}")
    public ResponseEntity<Map<String, String>> deletePet(@PathVariable("petIdx") Long petIdx) {
        String userId = getCurrentUserId();
        petService.deletePet(petIdx, userId);
        return ResponseEntity.ok(Map.of("message", "펫이 삭제되었습니다."));
    }

    /**
     * 펫 복구
     */
    @PostMapping("/{petIdx}/restore")
    public ResponseEntity<PetDTO> restorePet(@PathVariable("petIdx") Long petIdx) {
        String userId = getCurrentUserId();
        PetDTO restored = petService.restorePet(petIdx, userId);
        return ResponseEntity.ok(restored);
    }

    /**
     * 펫 타입별 조회
     */
    @GetMapping("/type/{petType}")
    public ResponseEntity<List<PetDTO>> getPetsByType(@PathVariable("petType") String petType) {
        List<PetDTO> pets = petService.getPetsByType(petType);
        return ResponseEntity.ok(pets);
    }
}
