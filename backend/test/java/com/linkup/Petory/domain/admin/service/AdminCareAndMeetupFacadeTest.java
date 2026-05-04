package com.linkup.Petory.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.exception.CareRequestNotFoundException;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.care.service.CareRequestService;
import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.service.MeetupService;

@ExtendWith(MockitoExtension.class)
class AdminCareAndMeetupFacadeTest {

    @InjectMocks
    private AdminCareAndMeetupFacade facade;

    @Mock
    private CareRequestRepository careRequestRepository;
    @Mock
    private CareRequestConverter careRequestConverter;
    @Mock
    private CareRequestService careRequestService;
    @Mock
    private MeetupService meetupService;
    @Mock
    private AdminAuditService auditService;

    @Test
    @DisplayName("정상: 관리자는 삭제된 care request도 상세 조회할 수 있다")
    void 정상_삭제된CareRequest_상세조회() {
        CareRequest entity = CareRequest.builder().build();
        entity.setIsDeleted(true);
        CareRequestDTO dto = CareRequestDTO.builder().idx(1L).build();
        when(careRequestRepository.findByIdWithApplications(1L)).thenReturn(Optional.of(entity));
        when(careRequestConverter.toDTO(entity)).thenReturn(dto);

        CareRequestDTO result = facade.getCareRequest(1L);

        assertThat(result).isSameAs(dto);
    }

    @Test
    @DisplayName("예외: 존재하지 않는 care request 상세 조회는 실패한다")
    void 예외_존재하지않는CareRequest_상세조회() {
        when(careRequestRepository.findByIdWithApplications(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getCareRequest(999L))
                .isInstanceOf(CareRequestNotFoundException.class);
    }

    @Test
    @DisplayName("정상: meetup 목록 조회 시 status와 keyword를 포함한 admin 전용 조회를 사용한다")
    void 정상_meetup목록_관리자필터조회() {
        Page<MeetupDTO> page = new PageImpl<>(java.util.List.of(MeetupDTO.builder().idx(1L).build()));
        when(meetupService.getMeetupsForAdmin("RECRUITING", "서울", PageRequest.of(0, 20))).thenReturn(page);

        Page<MeetupDTO> result = facade.getMeetups("RECRUITING", "서울", 0, 20);

        assertThat(result).isSameAs(page);
    }
}
