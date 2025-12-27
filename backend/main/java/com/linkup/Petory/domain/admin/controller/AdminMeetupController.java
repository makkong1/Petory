package com.linkup.Petory.domain.admin.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
import com.linkup.Petory.domain.meetup.service.MeetupService;

import lombok.RequiredArgsConstructor;

/**
 * 산책모임 관리 컨트롤러 (관리자용)
 * - ADMIN과 MASTER 모두 접근 가능
 * - 모임 목록 조회, 상태 변경, 삭제
 * - 참가자 관리
 */
@RestController
@RequestMapping("/api/admin/meetups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminMeetupController {

    private final MeetupService meetupService;

    /**
     * 모임 목록 조회 (필터링 지원)
     */
    @GetMapping
    public ResponseEntity<List<MeetupDTO>> listMeetups(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String q) {
        
        List<MeetupDTO> all = meetupService.getAllMeetups();
        
        // 상태 필터
        if (status != null && !status.equals("ALL")) {
            try {
                MeetupStatus statusEnum = MeetupStatus.valueOf(status.toUpperCase());
                all = all.stream()
                        .filter(m -> m.getStatus() != null && m.getStatus().equals(statusEnum.name()))
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // 잘못된 status 값은 무시
            }
        }
        
        // 검색어 필터
        if (q != null && !q.isBlank()) {
            String keyword = q.toLowerCase();
            all = all.stream()
                    .filter(m -> (m.getTitle() != null && m.getTitle().toLowerCase().contains(keyword))
                            || (m.getDescription() != null && m.getDescription().toLowerCase().contains(keyword))
                            || (m.getLocation() != null && m.getLocation().toLowerCase().contains(keyword))
                            || (m.getOrganizerName() != null && m.getOrganizerName().toLowerCase().contains(keyword)))
                    .collect(Collectors.toList());
        }
        
        return ResponseEntity.ok(all);
    }

    /**
     * 모임 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<MeetupDTO> getMeetup(@PathVariable Long id) {
        return ResponseEntity.ok(meetupService.getMeetupById(id));
    }

    /**
     * 모임 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeetup(@PathVariable Long id) {
        meetupService.deleteMeetup(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 모임 참가자 목록 조회
     */
    @GetMapping("/{id}/participants")
    public ResponseEntity<List<MeetupParticipantsDTO>> getParticipants(@PathVariable Long id) {
        return ResponseEntity.ok(meetupService.getMeetupParticipants(id));
    }
}

