package com.linkup.Petory.domain.recommendation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.recommendation.dto.RecommendCopyRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendCopyResponse;
import com.linkup.Petory.domain.recommendation.dto.RecommendEventRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;
import com.linkup.Petory.domain.recommendation.dto.TrendTimeseriesResponse;
import com.linkup.Petory.domain.recommendation.service.RecommendService;
import com.linkup.Petory.domain.user.exception.UnauthenticatedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    @GetMapping
    public ResponseEntity<RecommendResponse> recommend(
            @RequestParam(value = "lat") double lat,
            @RequestParam(value = "lng") double lng,
            @RequestParam(value = "context") String context) {
        String userId = requireUserId();

        log.info("추천 요청 — userId={}, lat={}, lng={}, context={}", userId, lat, lng, context);

        RecommendResponse response = recommendService.recommend(userId, lat, lng, context);
        if (response == null) {
            return ResponseEntity.status(503).build();
        }
        log.info(
                "[RecommendController→FE] 사용자 응답 요약 request_id={} lat={} lng={} ctx={} facilities={} trends={} version={}",
                response.requestId(), lat, lng, context,
                response.facilities() != null ? response.facilities().size() : 0,
                response.trends() != null ? response.trends().size() : 0,
                response.recommendVersion());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/copy")
    public ResponseEntity<RecommendCopyResponse> recommendCopy(@RequestBody RecommendCopyRequest body) {
        String userId = requireUserId();

        log.info("추천 카피 요청 — userId={}, context={}, requestId={}",
                userId, body.context(), body.requestId());

        RecommendCopyResponse response = recommendService.recommendCopy(userId, body);
        if (response == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/events")
    public ResponseEntity<Void> recordEvents(@RequestBody RecommendEventRequest body) {
        String userId = requireUserId();

        int count = body.events() != null ? body.events().size() : 0;
        log.info("추천 이벤트 요청 — userId={}, requestId={}, count={}",
                userId, body.requestId(), count);

        recommendService.recordEvents(userId, body);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/trends/{category}/timeseries")
    public ResponseEntity<TrendTimeseriesResponse> getTrendTimeseries(
            @PathVariable("category") String category,
            @RequestParam(name = "days", defaultValue = "14") int days,
            @RequestParam(name = "top_keywords", defaultValue = "10") int topKeywords) {
        String userId = requireUserId();

        log.info("트렌드 시계열 요청 — userId={}, category={}, days={}, top_keywords={}",
                userId, category, days, topKeywords);

        TrendTimeseriesResponse response = recommendService.getTrendTimeseries(category, days, topKeywords);
        if (response == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(response);
    }

    private String requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new UnauthenticatedException();
        }
        return auth.getName();
    }
}
