package com.linkup.Petory.domain.activity.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.activity.dto.ActivityDTO;
import com.linkup.Petory.domain.activity.service.ActivityService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/my")
    public ResponseEntity<List<ActivityDTO>> getMyActivities(@RequestParam Long userId) {
        System.out.println("=== [ActivityController] /api/activities/my 호출됨 - userId: " + userId + " ===");
        return ResponseEntity.ok(activityService.getUserActivities(userId));
    }
}
