package com.linkup.Petory.domain.user.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.user.dto.PetDTO;
import com.linkup.Petory.domain.user.service.PetService;
import com.linkup.Petory.global.security.CustomUserDetails;

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

    @GetMapping("/type/{petType}")
    public ResponseEntity<List<PetDTO>> getPetsByType(@PathVariable("petType") String petType) {
        List<PetDTO> pets = petService.getPetsByType(petType);
        return ResponseEntity.ok(pets);
    }
}
