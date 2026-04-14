package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.exception.CareRequestNotFoundException;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.service.PetCoinEscrowService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.PetRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class CareRequestServiceTest {

    @InjectMocks
    private CareRequestService careRequestService;

    @Mock
    private CareRequestRepository careRequestRepository;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private CareRequestConverter careRequestConverter;
    @Mock
    private PetCoinEscrowService petCoinEscrowService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Users createUser(Long idx) {
        return Users.builder().idx(idx).id("user_" + idx).build();
    }

    private CareRequest createOpenRequest(Long idx, Users user) {
        CareRequest request = CareRequest.builder()
                .user(user)
                .title("테스트 요청")
                .description("테스트 설명")
                .date(LocalDateTime.now().plusDays(1))
                .status(CareRequestStatus.OPEN)
                .build();
        request.setIdx(idx);
        request.setApplications(new ArrayList<>());
        return request;
    }

    private void setSecurityContext(Long userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ===== updateStatus 테스트 =====

    @Test
    @DisplayName("정상: OPEN → COMPLETED 상태 변경 시 completedAt 기록")
    void 정상_상태변경_COMPLETED() {
        Users user = createUser(1L);
        CareRequest request = createOpenRequest(1L, user);

        setSecurityContext(1L);

        when(careRequestRepository.findByIdWithApplications(1L)).thenReturn(Optional.of(request));
        when(careRequestRepository.save(any(CareRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(petCoinEscrowService.findByCareRequestForUpdate(any())).thenReturn(null);
        when(careRequestConverter.toDTO(any(CareRequest.class)))
                .thenReturn(CareRequestDTO.builder().status("COMPLETED").build());

        CareRequestDTO result = careRequestService.updateStatus(1L, "COMPLETED", 1L);

        assertThat(result).isNotNull();
        assertThat(request.getStatus()).isEqualTo(CareRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("예외: 삭제된 요청 상태 변경 시 CareRequestNotFoundException")
    void 예외_삭제된_요청_상태변경() {
        Users user = createUser(1L);
        CareRequest request = createOpenRequest(1L, user);
        request.setIsDeleted(true);

        when(careRequestRepository.findByIdWithApplications(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> careRequestService.updateStatus(1L, "COMPLETED", 1L))
                .isInstanceOf(CareRequestNotFoundException.class);

        verify(careRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("예외: 존재하지 않는 요청 상태 변경 시 CareRequestNotFoundException")
    void 예외_존재하지않는_요청_상태변경() {
        when(careRequestRepository.findByIdWithApplications(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> careRequestService.updateStatus(999L, "COMPLETED", 1L))
                .isInstanceOf(CareRequestNotFoundException.class);
    }

    // ===== getCareRequest 테스트 =====

    @Test
    @DisplayName("경계: 삭제된 요청 단건 조회 시 CareRequestNotFoundException")
    void 경계_삭제된_요청_단건조회() {
        Users user = createUser(1L);
        CareRequest request = createOpenRequest(1L, user);
        request.setIsDeleted(true);

        when(careRequestRepository.findByIdWithApplications(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> careRequestService.getCareRequest(1L))
                .isInstanceOf(CareRequestNotFoundException.class);
    }

    // ===== getMyCareRequests 테스트 =====

    @Test
    @DisplayName("정상: 내 케어 요청 조회 (userId 기반)")
    void 정상_내_케어요청_조회() {
        Users user = createUser(1L);
        CareRequest request = createOpenRequest(1L, user);
        CareRequestDTO dto = CareRequestDTO.builder().title("테스트 요청").build();

        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
        when(careRequestRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user))
                .thenReturn(List.of(request));
        when(careRequestConverter.toDTOList(any())).thenReturn(List.of(dto));

        List<CareRequestDTO> result = careRequestService.getMyCareRequests(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("테스트 요청");
    }

    @Test
    @DisplayName("경계: 존재하지 않는 사용자 ID로 조회 시 예외")
    void 경계_존재하지않는_사용자_내요청조회() {
        when(usersRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> careRequestService.getMyCareRequests(999L))
                .isInstanceOf(RuntimeException.class);
    }
}
