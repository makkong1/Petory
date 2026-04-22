package com.linkup.Petory.domain.recommendation.controller;

import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;
import com.linkup.Petory.domain.recommendation.service.RecommendService;
import com.linkup.Petory.domain.user.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    @GetMapping
    public ResponseEntity<RecommendResponse> recommend(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam String context
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new UnauthenticatedException();
        }
        String userId = auth.getName();

        RecommendResponse response = recommendService.recommend(userId, lat, lng, context);
        return ResponseEntity.ok(response);
    }
}
