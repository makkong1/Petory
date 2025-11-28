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

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    // 기존 API (하위 호환성 유지)
    @GetMapping("/my")
    public ResponseEntity<List<ActivityDTO>> getMyActivities(@RequestParam Long userId) {
        System.out.println("=== [ActivityController] /api/activities/my 호출됨 - userId: " + userId + " ===");
        return ResponseEntity.ok(activityService.getUserActivities(userId));
    }

    // 페이징 지원 API
    @GetMapping("/my/paging")
    public ResponseEntity<ActivityPageResponseDTO> getMyActivitiesWithPaging(
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        System.out.println("=== [ActivityController] /api/activities/my/paging 호출됨 - userId: " + userId 
                + ", filter: " + filter + ", page: " + page + ", size: " + size + " ===");
        return ResponseEntity.ok(activityService.getUserActivitiesWithPaging(userId, filter, page, size));
    }
}
