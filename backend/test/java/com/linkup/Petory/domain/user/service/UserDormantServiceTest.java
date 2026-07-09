package com.linkup.Petory.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class UserDormantServiceTest {

    @InjectMocks
    private UserDormantService userDormantService;

    @Mock
    private UsersRepository usersRepository;

    @Test
    @DisplayName("정상: cutoff를 현재로부터 1년 전으로 계산해 리포지토리에 위임한다")
    void 정상_cutoff_1년전_계산() {
        when(usersRepository.markDormantUsers(any(), any())).thenReturn(3);

        int updated = userDormantService.markDormantUsers();

        assertThat(updated).isEqualTo(3);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(usersRepository).markDormantUsers(cutoffCaptor.capture(), nowCaptor.capture());

        LocalDateTime expectedCutoff = LocalDateTime.now().minusYears(1);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(5, ChronoUnit.SECONDS));
    }
}
