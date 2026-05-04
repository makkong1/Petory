package com.linkup.Petory.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.linkup.Petory.domain.user.converter.PetConverter;
import com.linkup.Petory.domain.user.converter.UsersConverter;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
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
}
