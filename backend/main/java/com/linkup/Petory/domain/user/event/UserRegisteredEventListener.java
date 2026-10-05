package com.linkup.Petory.domain.user.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.service.EmailVerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원가입 커밋 후 인증 상태를 정리한다. 가입 전 인증을 마쳤으면 Redis 인증 기록을 지우고, 아니면 인증 메일을 보낸다.
 * 둘 다 롤백으로 되돌릴 수 없어서 AFTER_COMMIT에서만 실행한다. DB 쓰기가 없으므로 새 트랜잭션은 열지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private final EmailVerificationService emailVerificationService;

    @Value("${app.email-verification.skip-in-dev:false}")
    private boolean skipInDev;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event.isPreVerified()) {
            emailVerificationService.removePreRegistrationVerification(event.email());
            log.info("회원가입 완료 및 이메일 인증 상태 적용: userId={}, email={}", event.userId(), event.email());
        } else if (!skipInDev) {
            try {
                emailVerificationService.sendVerificationEmail(event.userId(), EmailVerificationPurpose.REGISTRATION);
                log.info("회원가입 후 이메일 인증 메일 발송: userId={}, email={}", event.userId(), event.email());
            } catch (Exception e) {
                // 이메일 발송 실패해도 회원가입은 성공으로 처리 (이미 커밋됨)
                log.error("회원가입 이메일 인증 메일 발송 실패: userId={}, error={}", event.userId(), e.getMessage(), e);
            }
        }
    }
}
