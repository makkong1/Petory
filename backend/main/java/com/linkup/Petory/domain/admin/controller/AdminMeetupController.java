package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminCareAndMeetupFacade;
import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/meetups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
/** 관리자용 모임 목록 조회·삭제·참여자 조회 API. [ADMIN, MASTER] */
public class AdminMeetupController {

    private final AdminCareAndMeetupFacade facade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<Page<MeetupDTO>> listMeetups(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(facade.getMeetups(status, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetupDTO> getMeetup(@PathVariable("id") Long id) {
        return ResponseEntity.ok(facade.getMeetup(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeetup(@PathVariable("id") Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        facade.deleteMeetup(id, adminIdx);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/participants")
    public ResponseEntity<List<MeetupParticipantsDTO>> getParticipants(@PathVariable("id") Long id) {
        return ResponseEntity.ok(facade.getMeetupParticipants(id));
    }
}
