package com.linkup.Petory.domain.petRecommendation.controller;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.service.PetRecommendationService;
import com.linkup.Petory.domain.petRecommendation.service.UserPetIntentSignalService;
import com.linkup.Petory.domain.petRecommendation.service.PlaceInteractionService;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pet-recommend")
@RequiredArgsConstructor
public class PetRecommendationController {

    private final PetRecommendationService   petRecommendationService;
    private final UserPetIntentSignalService signalService;
    private final PlaceInteractionService    interactionService;
    private final UsersRepository            usersRepository;

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
    public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(List.of());
        }
        Long userIdx = usersRepository.findActiveByIdString(userDetails.getUsername())
                .orElseThrow().getIdx();
        return ResponseEntity.ok(signalService.getActiveSignals(userIdx));
    }

    @PostMapping("/interact")
    public ResponseEntity<Void> interact(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("locationIdx") Long locationIdx,
            @RequestParam("type") String interactionType) {
        Long userIdx = usersRepository.findActiveByIdString(userDetails.getUsername())
                .orElseThrow().getIdx();
        interactionService.record(userIdx, locationIdx, interactionType);
        return ResponseEntity.ok().build();
    }
}
