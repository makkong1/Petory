package com.linkup.Petory.domain.meetup.controller;

import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.service.MeetupService;
import com.linkup.Petory.domain.user.exception.UnauthenticatedException;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meetups")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MeetupController {

    private final MeetupService meetupService;

    // 모임 생성
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMeetup(
            @RequestBody MeetupDTO meetupDTO,
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
    @PutMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> updateMeetup(@PathVariable Long meetupIdx,
            @RequestBody MeetupDTO meetupDTO) {
        MeetupDTO updatedMeetup = meetupService.updateMeetup(meetupIdx, meetupDTO);

        Map<String, Object> response = new HashMap<>();
        response.put("meetup", updatedMeetup);
        response.put("message", "모임이 성공적으로 수정되었습니다.");

        return ResponseEntity.ok(response);
    }

    // 모임 삭제
    @DeleteMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> deleteMeetup(@PathVariable Long meetupIdx) {
        meetupService.deleteMeetup(meetupIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "모임이 성공적으로 삭제되었습니다.");

        return ResponseEntity.ok(response);
    }

    // 모든 모임 조회
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllMeetups() {
        List<MeetupDTO> meetups = meetupService.getAllMeetups();

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    // 특정 모임 조회
    @GetMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> getMeetupById(@PathVariable Long meetupIdx) {
        MeetupDTO meetup = meetupService.getMeetupById(meetupIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("meetup", meetup);

        return ResponseEntity.ok(response);
    }

    // 지역별 모임 조회
    @GetMapping("/location")
    public ResponseEntity<Map<String, Object>> getMeetupsByLocation(
            @RequestParam Double minLat,
            @RequestParam Double maxLat,
            @RequestParam Double minLng,
            @RequestParam Double maxLng) {
        List<MeetupDTO> meetups = meetupService.getMeetupsByLocation(minLat, maxLat, minLng, maxLng);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    // 키워드로 모임 검색
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchMeetupsByKeyword(@RequestParam String keyword) {
        List<MeetupDTO> meetups = meetupService.searchMeetupsByKeyword(keyword);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    // 참여 가능한 모임 조회
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailableMeetups() {
        List<MeetupDTO> meetups = meetupService.getAvailableMeetups();

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    // 주최자별 모임 조회
    @GetMapping("/organizer/{organizerIdx}")
    public ResponseEntity<Map<String, Object>> getMeetupsByOrganizer(@PathVariable Long organizerIdx) {
        List<MeetupDTO> meetups = meetupService.getMeetupsByOrganizer(organizerIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    // 반경 기반 모임 조회 (마커 표시용)
    @GetMapping("/nearby")
    public ResponseEntity<Map<String, Object>> getNearbyMeetups(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5.0") Double radius) {
        List<MeetupDTO> meetups = meetupService.getNearbyMeetups(lat, lng, radius);

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", meetups);
        response.put("count", meetups.size());

        return ResponseEntity.ok(response);
    }

    // 특정 모임의 참가자 목록 조회
    @GetMapping("/{meetupIdx}/participants")
    public ResponseEntity<Map<String, Object>> getMeetupParticipants(@PathVariable Long meetupIdx) {
        List<MeetupParticipantsDTO> participants = meetupService.getMeetupParticipants(meetupIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("participants", participants);
        response.put("count", participants.size());

        return ResponseEntity.ok(response);
    }

    // 모임 참가
    @PostMapping("/{meetupIdx}/participants")
    public ResponseEntity<Map<String, Object>> joinMeetup(
            @PathVariable Long meetupIdx,
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
            @PathVariable Long meetupIdx,
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
            @PathVariable Long meetupIdx,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        boolean isParticipating = meetupService.isUserParticipating(meetupIdx, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("isParticipating", isParticipating);

        return ResponseEntity.ok(response);
    }
}
