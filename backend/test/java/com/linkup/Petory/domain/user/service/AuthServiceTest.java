package com.linkup.Petory.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.user.converter.UsersConverter;
import com.linkup.Petory.domain.user.dto.TokenResponse;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.InvalidRefreshTokenException;
import com.linkup.Petory.domain.user.exception.UserBannedException;
import com.linkup.Petory.domain.user.exception.UserSuspendedException;
import com.linkup.Petory.domain.user.repository.LoginEventRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private UsersConverter usersConverter;
    @Mock
    private LoginEventRepository loginEventRepository;

    @Test
    @DisplayName("예외: 소프트 삭제 계정의 refresh token은 재발급에 사용할 수 없다")
    void 예외_삭제계정_refresh차단() {
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(usersRepository.findActiveByRefreshToken("refresh-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshAccessToken("refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh Token을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("예외: 영구 차단 계정은 refresh token으로 access token을 재발급받을 수 없다")
    void 예외_차단계정_refresh차단() {
        Users user = Users.builder()
                .id("banned-user")
                .role(Role.USER)
                .status(UserStatus.BANNED)
                .refreshToken("refresh-token")
                .refreshExpiration(LocalDateTime.now().plusHours(1))
                .build();
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(usersRepository.findActiveByRefreshToken("refresh-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshAccessToken("refresh-token"))
                .isInstanceOf(UserBannedException.class);

        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getRefreshExpiration()).isNull();
        verify(usersRepository).save(user);
    }

    @Test
    @DisplayName("예외: 이용제한 계정은 정지 만료 전 refresh token으로 access token을 재발급받을 수 없다")
    void 예외_정지계정_refresh차단() {
        LocalDateTime suspendedUntil = LocalDateTime.now().plusDays(1);
        Users user = Users.builder()
                .id("suspended-user")
                .role(Role.USER)
                .status(UserStatus.SUSPENDED)
                .suspendedUntil(suspendedUntil)
                .refreshToken("refresh-token")
                .refreshExpiration(LocalDateTime.now().plusHours(1))
                .build();
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(usersRepository.findActiveByRefreshToken("refresh-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshAccessToken("refresh-token"))
                .isInstanceOf(UserSuspendedException.class);

        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getRefreshExpiration()).isNull();
        verify(usersRepository).save(user);
    }

    @Test
    @DisplayName("정상: 만료된 이용제한 계정은 refresh 시 ACTIVE로 전환한 뒤 access token을 재발급받는다")
    void 정상_만료정지_refresh자동해제() {
        Users user = Users.builder()
                .id("expired-suspended-user")
                .role(Role.USER)
                .status(UserStatus.SUSPENDED)
                .suspendedUntil(LocalDateTime.now().minusMinutes(1))
                .refreshToken("refresh-token")
                .refreshExpiration(LocalDateTime.now().plusHours(1))
                .build();
        UsersDTO dto = UsersDTO.builder().id("expired-suspended-user").build();
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(usersRepository.findActiveByRefreshToken("refresh-token")).thenReturn(Optional.of(user));
        when(jwtUtil.createAccessToken("expired-suspended-user")).thenReturn("new-access-token");
        when(usersConverter.toDTO(user)).thenReturn(dto);

        TokenResponse response = authService.refreshAccessToken("refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getSuspendedUntil()).isNull();
        verify(usersRepository).save(user);
    }

    @Test
    @DisplayName("정상: 활성 계정의 refresh token은 검증된다")
    void 정상_활성계정_refresh검증() {
        Users user = Users.builder()
                .id("active-user")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .refreshToken("refresh-token")
                .refreshExpiration(LocalDateTime.now().plusHours(1))
                .build();
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(usersRepository.findActiveByRefreshToken("refresh-token")).thenReturn(Optional.of(user));

        boolean valid = authService.validateRefreshToken("refresh-token");

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("정상: 제재 계정의 refresh token 검증은 false를 반환한다")
    void 정상_제재계정_refresh검증_false() {
        Users user = Users.builder()
                .id("banned-user")
                .role(Role.USER)
                .status(UserStatus.BANNED)
                .refreshToken("refresh-token")
                .refreshExpiration(LocalDateTime.now().plusHours(1))
                .build();
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(usersRepository.findActiveByRefreshToken("refresh-token")).thenReturn(Optional.of(user));

        boolean valid = authService.validateRefreshToken("refresh-token");

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("정상: 로그아웃은 활성 사용자만 처리하고 refresh token을 제거한다")
    void 정상_로그아웃_refresh제거() {
        Users user = Users.builder()
                .id("active-user")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .refreshToken("refresh-token")
                .refreshExpiration(LocalDateTime.now().plusHours(1))
                .build();
        when(usersRepository.findActiveByIdString("active-user")).thenReturn(Optional.of(user));

        authService.logout("active-user");

        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getRefreshExpiration()).isNull();
        verify(usersRepository).save(user);
    }
}
