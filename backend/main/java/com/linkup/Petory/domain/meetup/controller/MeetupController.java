package com.linkup.Petory.domain.meetup.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupHistoryDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.service.MeetupService;
import com.linkup.Petory.domain.user.exception.UnauthenticatedException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 모임 생성·조회·참가·취소·좋아요 API.
 */
@RestController
@RequestMapping("/api/meetups")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MeetupController {

    private final MeetupService meetupService;

    // 모임 생성
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMeetup(
            @Valid @RequestBody MeetupDTO meetupDTO,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        MeetupDTO createdMeetup = meetupService.createMeetup(meetupDTO, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("meetup", createdMeetup);
        response.put("message", "모임이 성공적으로 생성되었습니다.");

        return ResponseEntity.ok(response);
    }

    // 모임 수정
    // [FIX] Authentication 추가 → 서비스 레벨 주최자 검증 연동
    @PutMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> updateMeetup(
            @PathVariable("meetupIdx") Long meetupIdx,
            @RequestBody MeetupDTO meetupDTO,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }
        MeetupDTO updatedMeetup = meetupService.updateMeetup(meetupIdx, meetupDTO, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("meetup", updatedMeetup);
        response.put("message", "모임이 성공적으로 수정되었습니다.");

        return ResponseEntity.ok(response);
    }

    // 모임 삭제
    // [FIX] Authentication 추가 → 서비스 레벨 주최자 검증 연동
    @DeleteMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> deleteMeetup(
            @PathVariable("meetupIdx") Long meetupIdx,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }
        meetupService.deleteMeetup(meetupIdx, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "모임이 성공적으로 삭제되었습니다.");

        return ResponseEntity.ok(response);
    }

    // 모든 모임 조회 (페이징: page, size — 생략 시 page=0, size=20)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllMeetups(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MeetupDTO> result = meetupService.getAllMeetups(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", result.getContent());
        response.put("count", result.getNumberOfElements());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());

        return ResponseEntity.ok(response);
    }

    // 특정 모임 조회
    @GetMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> getMeetupById(
            @PathVariable("meetupIdx") Long meetupIdx) {
        MeetupDTO meetup = meetupService.getMeetupById(meetupIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("meetup", meetup);

        return ResponseEntity.ok(response);
    }

    // 키워드로 모임 검색
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchMeetupsByKeyword(@RequestParam(value = "keyword") String keyword) {
        List<MeetupDTO> meetups = meetupService.searchMeetupsByKeyword(keyword);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    // 참여 가능한 모임 조회 (페이징: page, size — 생략 시 page=0, size=20)
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailableMeetups(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "date"));
        Slice<MeetupDTO> slice = meetupService.getAvailableMeetups(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", slice.getContent());
        response.put("count", slice.getNumberOfElements());
        response.put("hasNext", slice.hasNext());
        response.put("page", slice.getNumber());
        response.put("size", slice.getSize());

        return ResponseEntity.ok(response);
    }

    // 주최자별 모임 조회
    @GetMapping("/organizer/{organizerIdx}")
    public ResponseEntity<Map<String, Object>> getMeetupsByOrganizer(
            @PathVariable("organizerIdx") Long organizerIdx) {
        List<MeetupDTO> meetups = meetupService.getMeetupsByOrganizer(organizerIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    // 반경 기반 모임 조회 (마커 표시용, maxResults 상한 기본 500)
    @GetMapping("/nearby")
    public ResponseEntity<Map<String, Object>> getNearbyMeetups(
            @RequestParam(value = "lat") Double lat,
            @RequestParam(value = "lng") Double lng,
            @RequestParam(value = "radius", defaultValue = "5.0") Double radius,
            @RequestParam(value = "maxResults", defaultValue = "500") int maxResults) {
        List<MeetupDTO> meetups = meetupService.getNearbyMeetups(lat, lng, radius, maxResults);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/home")
    public ResponseEntity<Map<String, Object>> getHomeMeetups(
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "size", defaultValue = "6") int size) {
        List<MeetupDTO> meetups = meetupService.getHomeMeetups(lat, lng, size);
        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());
        return ResponseEntity.ok(response);
    }

    // 특정 모임의 참가자 목록 조회
    @GetMapping("/{meetupIdx}/participants")
    public ResponseEntity<Map<String, Object>> getMeetupParticipants(
            @PathVariable("meetupIdx") Long meetupIdx) {
        List<MeetupParticipantsDTO> participants = meetupService.getMeetupParticipants(meetupIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("participants", participants);
        response.put("count", participants.size());

        return ResponseEntity.ok(response);
    }

    // 모임 참가
    @PostMapping("/{meetupIdx}/participants")
    public ResponseEntity<Map<String, Object>> joinMeetup(
            @PathVariable("meetupIdx") Long meetupIdx,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        MeetupParticipantsDTO participant = meetupService.joinMeetup(meetupIdx, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("participant", participant);
        response.put("message", "모임에 참가했습니다.");

        return ResponseEntity.ok(response);
    }

    // 모임 참가 취소
    @DeleteMapping("/{meetupIdx}/participants")
    public ResponseEntity<Map<String, Object>> cancelMeetupParticipation(
            @PathVariable("meetupIdx") Long meetupIdx,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        meetupService.cancelMeetupParticipation(meetupIdx, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "모임 참가를 취소했습니다.");

        return ResponseEntity.ok(response);
    }

    // 사용자가 특정 모임에 참가했는지 확인
    @GetMapping("/{meetupIdx}/participants/check")
    public ResponseEntity<Map<String, Object>> checkParticipation(
            @PathVariable("meetupIdx") Long meetupIdx,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        MeetupHistoryDTO participation = meetupService.getMyMeetupParticipation(meetupIdx, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("isParticipating", participation != null);
        response.put("liked", participation != null && Boolean.TRUE.equals(participation.getLiked()));

        return ResponseEntity.ok(response);
    }

    // 내 모임 히스토리 좋아요 표시/해제
    @PatchMapping("/{meetupIdx}/history/like")
    public ResponseEntity<Map<String, Object>> updateMyMeetupLike(
            @PathVariable("meetupIdx") Long meetupIdx,
            @RequestParam(value = "liked") boolean liked,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        MeetupHistoryDTO history = meetupService.updateMyMeetupLike(meetupIdx, userId, liked);

        Map<String, Object> response = new HashMap<>();
        response.put("history", history);
        response.put("message", liked ? "모임 기록에 좋아요를 표시했습니다." : "모임 기록 좋아요를 해제했습니다.");

        return ResponseEntity.ok(response);
    }

}
