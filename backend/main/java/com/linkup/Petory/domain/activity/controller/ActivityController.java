package com.linkup.Petory.domain.activity.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.activity.dto.ActivityDTO;
import com.linkup.Petory.domain.activity.dto.ActivityPageResponseDTO;
import com.linkup.Petory.domain.activity.service.ActivityService;
import com.linkup.Petory.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자 활동 내역(케어 요청·게시글·댓글 등) 조회 API.
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Slf4j
public class ActivityController {

    private final ActivityService activityService;

    // 기존 API (하위 호환성 유지)
    // 대상은 인증 주체다. 클라이언트가 보낸 userId 를 쓰면 남의 활동 내역이 조회된다.
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ActivityDTO>> getMyActivities(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(activityService.getUserActivities(userDetails.getIdx()));
    }

    // 페이징 지원 API
    @GetMapping("/my/paging")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ActivityPageResponseDTO> getMyActivitiesWithPaging(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "filter", required = false, defaultValue = "ALL") String filter,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(
                activityService.getUserActivitiesWithPaging(userDetails.getIdx(), filter, page, size));
    }
}
