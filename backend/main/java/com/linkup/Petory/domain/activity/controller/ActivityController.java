package com.linkup.Petory.domain.activity.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.activity.dto.ActivityDTO;
import com.linkup.Petory.domain.activity.dto.ActivityPageResponseDTO;
import com.linkup.Petory.domain.activity.service.ActivityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 사용자 활동 내역(케어 요청·게시글·댓글 등) 조회 API. */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Slf4j
public class ActivityController {

    private final ActivityService activityService;

    // 기존 API (하위 호환성 유지)
    @GetMapping("/my")
    public ResponseEntity<List<ActivityDTO>> getMyActivities(@RequestParam("userId") Long userId) {
        log.info("=== [ActivityController] /api/activities/my 호출됨 - userId: " + userId + " ===");
        return ResponseEntity.ok(activityService.getUserActivities(userId));
    }

    // 페이징 지원 API
    @GetMapping("/my/paging")
    public ResponseEntity<ActivityPageResponseDTO> getMyActivitiesWithPaging(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "filter", required = false, defaultValue = "ALL") String filter,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        log.info("=== [ActivityController] /api/activities/my/paging 호출됨 - userId: " + userId
                + ", filter: " + filter + ", page: " + page + ", size: " + size + " ===");
        return ResponseEntity.ok(activityService.getUserActivitiesWithPaging(userId, filter, page, size));
    }
}
