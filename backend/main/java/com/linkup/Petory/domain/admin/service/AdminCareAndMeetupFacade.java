package com.linkup.Petory.domain.admin.service;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.exception.CareRequestNotFoundException;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.care.service.CareRequestService;
import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.service.MeetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/** 관리자용 케어 요청·모임 조회·상태 변경·삭제를 처리하는 퍼사드. */
public class AdminCareAndMeetupFacade {

    private final CareRequestRepository careRequestRepository;
    private final CareRequestConverter careRequestConverter;
    private final CareRequestService careRequestService;
    private final MeetupService meetupService;
    private final AdminAuditService auditService;

    // ── 케어 요청 ────────────────────────────────────────────────────────

    public Page<CareRequestDTO> getCareRequests(String status, Boolean deleted, String keyword,
                                                 int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return careRequestRepository.findAllForAdmin(status, deleted, keyword, pageable)
                .map(careRequestConverter::toDTO);
    }

    public CareRequestDTO getCareRequest(Long id) {
        return careRequestRepository.findByIdWithApplications(id)
                .map(careRequestConverter::toDTO)
                .orElseThrow(CareRequestNotFoundException::new);
    }

    @Transactional
    public CareRequestDTO updateCareStatus(Long id, String status, Long adminIdx) {
        CareRequestDTO result = careRequestService.updateStatus(id, status, adminIdx);
        auditService.log(adminIdx, "CARE_STATUS_UPDATE", "CARE_REQUEST", id, "status=" + status);
        return result;
    }

    @Transactional
    public void deleteCareRequest(Long id, Long adminIdx) {
        careRequestService.deleteCareRequest(id, adminIdx);
        auditService.log(adminIdx, "CARE_DELETE", "CARE_REQUEST", id, null);
    }

    @Transactional
    public CareRequestDTO restoreCareRequest(Long id, Long adminIdx) {
        CareRequestDTO result = careRequestService.restoreForAdmin(id);
        auditService.log(adminIdx, "CARE_RESTORE", "CARE_REQUEST", id, null);
        return result;
    }

    // ── 모임 ─────────────────────────────────────────────────────────────

    public Page<MeetupDTO> getMeetups(String status, String keyword, int page, int size) {
        return meetupService.getMeetupsForAdmin(status, keyword, PageRequest.of(page, size));
    }

    public MeetupDTO getMeetup(Long id) {
        return meetupService.getMeetupById(id);
    }

    @Transactional
    public void deleteMeetup(Long id, Long adminIdx) {
        meetupService.deleteMeetupForAdmin(id);
        auditService.log(adminIdx, "MEETUP_DELETE", "MEETUP", id, null);
    }

    public List<MeetupParticipantsDTO> getMeetupParticipants(Long id) {
        return meetupService.getMeetupParticipants(id);
    }
}
