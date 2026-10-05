package com.linkup.Petory.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 인증 메일은 가입이 커밋된 뒤에만 나가야 한다. 트랜잭션 안에서 보내면 롤백돼도 메일은 되돌릴 수 없다.
 */
@SpringBootTest(properties = "app.email-verification.skip-in-dev=false")
class UsersServiceRegisterEventTest {

    @Autowired UsersService usersService;
    @Autowired UsersRepository usersRepository;
    @Autowired TransactionTemplate tx;

    @MockitoBean EmailVerificationService emailVerificationService;

    @Test
    void 가입이_롤백되면_인증_메일을_보내지_않는다() {
        UsersDTO dto = newUser("rollback");

        tx.executeWithoutResult(st -> {
            usersService.createUser(dto);
            st.setRollbackOnly();
        });

        assertThat(usersRepository.findByIdString(dto.getId())).isEmpty();
        verify(emailVerificationService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void 가입이_커밋되면_인증_메일을_한_번_보낸다() {
        UsersDTO dto = newUser("commit");

        try {
            tx.executeWithoutResult(st -> usersService.createUser(dto));

            verify(emailVerificationService).sendVerificationEmail(dto.getId(), EmailVerificationPurpose.REGISTRATION);
        } finally {
            tx.executeWithoutResult(st -> usersRepository.findByIdString(dto.getId())
                    .ifPresent(u -> usersRepository.deleteById(u.getIdx())));
        }
    }

    private UsersDTO newUser(String tag) {
        String s = tag + "_" + System.nanoTime();
        return UsersDTO.builder()
                .id("reg_" + s)
                .username("reg_" + s)
                .nickname("reg_" + s)
                .email("reg_" + s + "@test.com")
                .password("password123")
                .role("USER")
                .build();
    }
}
