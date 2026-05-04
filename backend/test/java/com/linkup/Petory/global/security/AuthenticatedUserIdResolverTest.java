package com.linkup.Petory.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UnauthenticatedException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserIdResolverTest {

    @InjectMocks
    private AuthenticatedUserIdResolver resolver;

    @Mock
    private UsersRepository usersRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("정상: 인증된 활성 사용자의 idx를 반환한다")
    void 정상_활성사용자_idx반환() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("active-user", null));
        when(usersRepository.findActiveByIdString("active-user"))
                .thenReturn(Optional.of(Users.builder().idx(10L).id("active-user").build()));

        long userIdx = resolver.requireCurrentUserIdx();

        assertThat(userIdx).isEqualTo(10L);
    }

    @Test
    @DisplayName("예외: 소프트 삭제된 사용자는 인증 사용자로 인정하지 않는다")
    void 예외_삭제사용자_인증거부() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("deleted-user", null));
        when(usersRepository.findActiveByIdString("deleted-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.requireCurrentUserIdx())
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("유효하지 않은 인증 사용자입니다.");
    }
}
