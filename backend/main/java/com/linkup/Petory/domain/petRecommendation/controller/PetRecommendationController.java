package com.linkup.Petory.domain.petRecommendation.controller;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.service.PetRecommendationService;
import com.linkup.Petory.domain.petRecommendation.service.UserPetIntentSignalService;
import com.linkup.Petory.domain.petRecommendation.service.PlaceInteractionService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pet-recommend")
@RequiredArgsConstructor
public class PetRecommendationController {

    private final PetRecommendationService   petRecommendationService;
    private final UserPetIntentSignalService signalService;
    private final PlaceInteractionService    interactionService;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<PetRecommendResponse> recommend(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam("text") String text,
            @RequestParam(name = "radius", defaultValue = "3000") int radius,
            @RequestParam(name = "petType", required = false) String petType) {
        return ResponseEntity.ok(
                petRecommendationService.recommend(text, lat, lng, radius, petType));
    }

    @GetMapping("/signals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals() {
        return ResponseEntity.ok(signalService.getActiveSignals(userIdResolver.requireCurrentUserIdx()));
    }

    @PostMapping("/interact")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> interact(
            @RequestParam("locationIdx") Long locationIdx,
            @RequestParam("type") String interactionType) {
        interactionService.record(userIdResolver.requireCurrentUserIdx(), locationIdx, interactionType);
        return ResponseEntity.ok().build();
    }
}
