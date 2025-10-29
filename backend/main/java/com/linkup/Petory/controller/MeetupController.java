package com.linkup.Petory.controller;

import com.linkup.Petory.dto.MeetupDTO;
import com.linkup.Petory.service.MeetupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/meetups")
@RequiredArgsConstructor
public class MeetupController {

    private final MeetupService meetupService;

    // 모임 생성
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMeetup(@RequestBody MeetupDTO meetupDTO) {
        try {
            MeetupDTO createdMeetup = meetupService.createMeetup(meetupDTO);

            Map<String, Object> response = new HashMap<>();
            response.put("meetup", createdMeetup);
            response.put("message", "모임이 성공적으로 생성되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모임 생성 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 모임 수정
    @PutMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> updateMeetup(@PathVariable Long meetupIdx,
            @RequestBody MeetupDTO meetupDTO) {
        try {
            MeetupDTO updatedMeetup = meetupService.updateMeetup(meetupIdx, meetupDTO);

            Map<String, Object> response = new HashMap<>();
            response.put("meetup", updatedMeetup);
            response.put("message", "모임이 성공적으로 수정되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모임 수정 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 모임 삭제
    @DeleteMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> deleteMeetup(@PathVariable Long meetupIdx) {
        try {
            meetupService.deleteMeetup(meetupIdx);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "모임이 성공적으로 삭제되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모임 삭제 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 모든 모임 조회
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllMeetups() {
        try {
            List<MeetupDTO> meetups = meetupService.getAllMeetups();

            Map<String, Object> response = new HashMap<>();
            response.put("meetups", meetups);
            response.put("count", meetups.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모임 목록 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 특정 모임 조회
    @GetMapping("/{meetupIdx}")
    public ResponseEntity<Map<String, Object>> getMeetupById(@PathVariable Long meetupIdx) {
        try {
            MeetupDTO meetup = meetupService.getMeetupById(meetupIdx);

            Map<String, Object> response = new HashMap<>();
            response.put("meetup", meetup);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모임 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 지역별 모임 조회
    @GetMapping("/location")
    public ResponseEntity<Map<String, Object>> getMeetupsByLocation(
            @RequestParam Double minLat,
            @RequestParam Double maxLat,
            @RequestParam Double minLng,
            @RequestParam Double maxLng) {
        try {
            List<MeetupDTO> meetups = meetupService.getMeetupsByLocation(minLat, maxLat, minLng, maxLng);

            Map<String, Object> response = new HashMap<>();
            response.put("meetups", meetups);
            response.put("count", meetups.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("지역별 모임 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 키워드로 모임 검색
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchMeetupsByKeyword(@RequestParam String keyword) {
        try {
            List<MeetupDTO> meetups = meetupService.searchMeetupsByKeyword(keyword);

            Map<String, Object> response = new HashMap<>();
            response.put("meetups", meetups);
            response.put("count", meetups.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모임 검색 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 참여 가능한 모임 조회
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailableMeetups() {
        try {
            List<MeetupDTO> meetups = meetupService.getAvailableMeetups();

            Map<String, Object> response = new HashMap<>();
            response.put("meetups", meetups);
            response.put("count", meetups.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("참여 가능한 모임 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 주최자별 모임 조회
    @GetMapping("/organizer/{organizerIdx}")
    public ResponseEntity<Map<String, Object>> getMeetupsByOrganizer(@PathVariable Long organizerIdx) {
        try {
            List<MeetupDTO> meetups = meetupService.getMeetupsByOrganizer(organizerIdx);

            Map<String, Object> response = new HashMap<>();
            response.put("meetups", meetups);
            response.put("count", meetups.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("주최자별 모임 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
