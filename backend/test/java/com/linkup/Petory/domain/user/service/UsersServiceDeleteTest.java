package com.linkup.Petory.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.linkup.Petory.domain.user.converter.PetConverter;
import com.linkup.Petory.domain.user.converter.UsersConverter;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.event.UserSanctionAppliedEvent;
import com.linkup.Petory.domain.user.exception.UserValidationException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class UsersServiceDeleteTest {

    @InjectMocks
    private UsersService usersService;

    @Mock
    private UsersRepository usersRepository;
    @Mock
    private UsersConverter usersConverter;
    @Mock
    private PetConverter petConverter;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("정상: 소프트 삭제 시 refresh token도 함께 제거한다")
    void 정상_소프트삭제_refresh제거() {
        Users user = Users.builder()
                .idx(1L)
                .id("user-1")
                .username("user-1")
                .email("user-1@test.local")
                .password("encoded")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .refreshToken("refresh-token")
                .refreshExpiration(LocalDateTime.now().plusDays(1))
                .isDeleted(false)
                .build();
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));

        usersService.deleteUser(1L);

        assertThat(user.getIsDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getRefreshExpiration()).isNull();
        verify(usersRepository).save(user);
    }

    @Test
    @DisplayName("예외: 관리자 상태 변경에서 SUSPENDED는 미래 suspendedUntil이 필요하다")
    void 예외_정지상태_미래해제일_필수() {
        Users user = baseUser();
        UsersDTO dto = UsersDTO.builder()
                .status("SUSPENDED")
                .suspendedUntil(LocalDateTime.now().minusMinutes(1))
                .build();
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> usersService.updateUserStatus(1L, dto))
                .isInstanceOf(UserValidationException.class)
                .hasMessageContaining("suspendedUntil");

        verify(usersRepository, never()).save(any(Users.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("정상: 관리자 상태 변경에서 유효한 SUSPENDED만 제재 이벤트를 발행한다")
    void 정상_유효한_정지만_제재이벤트_발행() {
        Users user = baseUser();
        LocalDateTime until = LocalDateTime.now().plusDays(3);
        UsersDTO dto = UsersDTO.builder()
                .status("SUSPENDED")
                .suspendedUntil(until)
                .build();
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
        when(usersRepository.save(user)).thenReturn(user);
        when(usersConverter.toDTO(user)).thenReturn(UsersDTO.builder().idx(1L).status("SUSPENDED").build());

        usersService.updateUserStatus(1L, dto);

        ArgumentCaptor<UserSanctionAppliedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserSanctionAppliedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(eventCaptor.getValue().suspendedUntil()).isEqualTo(until);
        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getRefreshExpiration()).isNull();
    }

    @Test
    @DisplayName("정상: 이미 제재 중인 사용자의 비상태 필드 수정은 제재 이벤트를 재발행하지 않는다")
    void 정상_비상태수정_제재이벤트_미발행() {
        Users user = baseUser();
        user.suspend(LocalDateTime.now().plusDays(3));
        UsersDTO dto = UsersDTO.builder()
                .warningCount(2)
                .build();
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
        when(usersRepository.save(user)).thenReturn(user);
        when(usersConverter.toDTO(user)).thenReturn(UsersDTO.builder().idx(1L).status("SUSPENDED").build());

        usersService.updateUserStatus(1L, dto);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private Users baseUser() {
        return Users.builder()
                .idx(1L)
                .id("user-1")
                .username("user-1")
                .email("user-1@test.local")
                .password("encoded")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .refreshToken("refresh-token")
                .refreshExpiration(LocalDateTime.now().plusDays(1))
                .isDeleted(false)
                .build();
    }
}
