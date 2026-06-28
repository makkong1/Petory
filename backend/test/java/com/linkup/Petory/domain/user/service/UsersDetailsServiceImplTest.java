package com.linkup.Petory.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class UsersDetailsServiceImplTest {

    @InjectMocks
    private UsersDetailsServiceImpl usersDetailsService;

    @Mock
    private UsersRepository usersRepository;

    @Test
    @DisplayName("정상: 소프트 삭제되지 않은 사용자는 인증 주체로 로드된다")
    void 정상_활성사용자_로드() {
        Users user = Users.builder()
                .id("active-user")
                .password("encoded-password")
                .role(Role.ADMIN)
                .build();
        when(usersRepository.findActiveByIdString("active-user")).thenReturn(Optional.of(user));

        UserDetails userDetails = usersDetailsService.loadUserByUsername("active-user");

        assertThat(userDetails.getUsername()).isEqualTo("active-user");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("정상: 만료된 이용제한 사용자는 인증 주체 로드 시 ACTIVE로 전환된다")
    void 정상_만료정지사용자_로드시_자동해제() {
        Users user = Users.builder()
                .id("expired-suspended-user")
                .password("encoded-password")
                .role(Role.USER)
                .status(UserStatus.SUSPENDED)
                .suspendedUntil(LocalDateTime.now().minusMinutes(1))
                .build();
        when(usersRepository.findActiveByIdString("expired-suspended-user")).thenReturn(Optional.of(user));
        when(usersRepository.save(user)).thenReturn(user);

        UserDetails userDetails = usersDetailsService.loadUserByUsername("expired-suspended-user");

        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getSuspendedUntil()).isNull();
        verify(usersRepository).save(user);
    }

    @Test
    @DisplayName("예외: 소프트 삭제된 사용자는 인증 주체로 로드되지 않는다")
    void 예외_삭제사용자_로드차단() {
        when(usersRepository.findActiveByIdString("deleted-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usersDetailsService.loadUserByUsername("deleted-user"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("deleted-user");
    }
}
