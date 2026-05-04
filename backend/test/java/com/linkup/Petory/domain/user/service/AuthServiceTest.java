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
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.InvalidRefreshTokenException;
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
