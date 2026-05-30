package com.linkup.Petory.domain.petRecommendation.controller;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendResponse;
import com.linkup.Petory.domain.petRecommendation.service.PetRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pet-recommend")
@RequiredArgsConstructor
public class PetRecommendationController {

    private final PetRecommendationService petRecommendationService;

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
}
