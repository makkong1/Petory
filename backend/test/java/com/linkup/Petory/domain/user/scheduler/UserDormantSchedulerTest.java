package com.linkup.Petory.domain.user.scheduler;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.user.service.UserDormantService;

@ExtendWith(MockitoExtension.class)
class UserDormantSchedulerTest {

    @InjectMocks
    private UserDormantScheduler userDormantScheduler;

    @Mock
    private UserDormantService userDormantService;

    @Test
    @DisplayName("정상: 스케줄러 실행 시 UserDormantService.markDormantUsers()를 호출한다")
    void 정상_배치실행_서비스호출() {
        userDormantScheduler.markDormantUsers();

        verify(userDormantService).markDormantUsers();
    }
}
