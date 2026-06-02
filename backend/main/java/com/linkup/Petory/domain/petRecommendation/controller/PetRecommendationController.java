package com.linkup.Petory.domain.petRecommendation.controller;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.service.PetRecommendationService;
import com.linkup.Petory.domain.petRecommendation.service.UserPetIntentSignalService;
import com.linkup.Petory.domain.petRecommendation.service.PlaceInteractionService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/pet-recommend")
@RequiredArgsConstructor
/**
 * 반려생활 추천 API 진입점.
 *
 * <p>추천 조회, 사용자 signal 조회, 장소 상호작용 기록 엔드포인트를 제공한다.
 */
public class PetRecommendationController {

    private final PetRecommendationService   petRecommendationService;
    private final UserPetIntentSignalService signalService;
    private final PlaceInteractionService    interactionService;
    private final AuthenticatedUserIdResolver userIdResolver;

    /** 자연어 텍스트와 사용자 위치를 받아 추천 시설 목록을 반환한다. */
    @GetMapping
    public ResponseEntity<PetRecommendResponse> recommend(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam("text") @Size(max = 500, message = "text는 500자 이하입니다") String text,
            @RequestParam(name = "radius", defaultValue = "3000") int radius,
            @RequestParam(name = "petType", required = false) String petType) {
        return ResponseEntity.ok(
                petRecommendationService.recommend(text, lat, lng, radius, petType));
    }

    /** 로그인 사용자의 최근 유효 의도 signal 카드 데이터를 조회한다. */
    @GetMapping("/signals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals() {
        return ResponseEntity.ok(signalService.getActiveSignals(userIdResolver.requireCurrentUserIdx()));
    }

    /** 추천 결과에서 발생한 사용자 행동(조회/길찾기/관심)을 로그로 적재한다. */
    @PostMapping("/interact")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> interact(
            @RequestParam("locationIdx") Long locationIdx,
            @RequestParam("type")
            @Pattern(regexp = "VIEW|NAVIGATE|FAVORITE", message = "VIEW, NAVIGATE, FAVORITE 중 하나여야 합니다")
            String interactionType) {
        interactionService.record(userIdResolver.requireCurrentUserIdx(), locationIdx, interactionType);
        return ResponseEntity.ok().build();
    }
}
