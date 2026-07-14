package com.linkup.Petory.domain.user.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.user.dto.PetDTO;
import com.linkup.Petory.domain.user.service.PetService;
import com.linkup.Petory.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애완동물 관리 컨트롤러 - 모든 인증된 사용자가 자신의 펫을 관리할 수 있음
 */
@Slf4j
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @GetMapping
    public ResponseEntity<List<PetDTO>> getMyPets(@AuthenticationPrincipal CustomUserDetails userDetails) {
        List<PetDTO> pets = petService.getPetsByUserId(userDetails.getLoginId());
        return ResponseEntity.ok(pets);
    }

    @GetMapping("/{petIdx}")
    public ResponseEntity<PetDTO> getPet(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("petIdx") Long petIdx) {
        PetDTO pet = petService.getPet(petIdx, userDetails.getLoginId());
        return ResponseEntity.ok(pet);
    }

    @PostMapping
    public ResponseEntity<PetDTO> createPet(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PetDTO dto) {
        PetDTO created = petService.createPet(userDetails.getLoginId(), dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{petIdx}")
    public ResponseEntity<PetDTO> updatePet(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("petIdx") Long petIdx,
            @RequestBody PetDTO dto) {
        PetDTO updated = petService.updatePet(petIdx, userDetails.getLoginId(), dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{petIdx}")
    public ResponseEntity<Map<String, String>> deletePet(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("petIdx") Long petIdx) {
        petService.deletePet(petIdx, userDetails.getLoginId());
        return ResponseEntity.ok(Map.of("message", "펫이 삭제되었습니다."));
    }

    @PostMapping("/{petIdx}/restore")
    public ResponseEntity<PetDTO> restorePet(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("petIdx") Long petIdx) {
        PetDTO restored = petService.restorePet(petIdx, userDetails.getLoginId());
        return ResponseEntity.ok(restored);
    }

    // 페이징 없이는 DOG 만 7,667건이 한 번에 나가고 백신 배치 쿼리가 154회 붙는다.
    // 근거: docs/analysis/query-audit/etc-domains-2026-07-14.md §1
    @GetMapping("/type/{petType}")
    public ResponseEntity<Page<PetDTO>> getPetsByType(
            @PathVariable("petType") String petType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(petService.getPetsByType(petType, PageRequest.of(page, size)));
    }
}
