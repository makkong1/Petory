package com.linkup.Petory.domain.admin.service;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
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
        return careRequestService.getCareRequest(id);
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
        Pageable pageable = PageRequest.of(page, size);
        Page<MeetupDTO> meetupPage = meetupService.getAllMeetups(pageable);

        if ((status != null && !"ALL".equals(status)) || (keyword != null && !keyword.isBlank())) {
            List<MeetupDTO> filtered = meetupPage.getContent().stream()
                    .filter(m -> {
                        boolean statusMatch = status == null || "ALL".equals(status) ||
                                (m.getStatus() != null && m.getStatus().equalsIgnoreCase(status));
                        boolean keywordMatch = keyword == null || keyword.isBlank() ||
                                (m.getTitle() != null && m.getTitle().toLowerCase().contains(keyword.toLowerCase())) ||
                                (m.getDescription() != null && m.getDescription().toLowerCase().contains(keyword.toLowerCase()));
                        return statusMatch && keywordMatch;
                    })
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, meetupPage.getTotalElements());
        }
        return meetupPage;
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
