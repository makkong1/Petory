package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증 서비스
 * 단일 통합 시스템으로 모든 용도의 이메일 인증 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationService {

    private final UsersRepository usersRepository;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    /**
     * 이메일 인증 메일 발송
     * @param userId 사용자 ID
     * @param purpose 인증 용도
     */
    public void sendVerificationEmail(String userId, EmailVerificationPurpose purpose) {
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new RuntimeException("이메일이 등록되지 않았습니다.");
        }

        // 이미 인증된 경우 스킵 (선택적)
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            log.info("이미 이메일 인증이 완료된 사용자: userId={}", userId);
            return;
        }

        // 인증 토큰 생성
        String token = jwtUtil.createEmailVerificationToken(userId, purpose);

        // 이메일 발송 (비동기)
        emailService.sendVerificationEmail(user.getEmail(), token, purpose);
        
        log.info("이메일 인증 메일 발송 요청: userId={}, purpose={}", userId, purpose);
    }

    /**
     * 이메일 인증 처리
     * @param token 인증 토큰
     * @return 인증 용도 (리다이렉트용)
     */
    public EmailVerificationPurpose verifyEmail(String token) {
        // 토큰 유효성 검증
        if (!jwtUtil.validateEmailVerificationToken(token)) {
            throw new RuntimeException("유효하지 않거나 만료된 인증 토큰입니다.");
        }

        // 토큰에서 사용자 ID와 용도 추출
        String userId = jwtUtil.extractUserIdFromEmailToken(token);
        EmailVerificationPurpose purpose = jwtUtil.extractPurposeFromEmailToken(token);

        if (userId == null || purpose == null) {
            throw new RuntimeException("토큰에서 정보를 추출할 수 없습니다.");
        }

        // 사용자 조회
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 이메일 인증 완료 (용도 무관, 단일 인증 상태로 업데이트)
        user.setEmailVerified(true);
        usersRepository.save(user);

        log.info("이메일 인증 완료: userId={}, purpose={}", userId, purpose);

        return purpose;
    }

    /**
     * 이메일 인증 여부 확인
     * @param userId 사용자 ID
     * @throws EmailVerificationRequiredException 인증이 필요한 경우
     */
    public void checkEmailVerification(String userId) {
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException("이메일 인증이 필요합니다.");
        }
    }

    /**
     * 이메일 인증 여부 확인 (boolean 반환)
     * @param userId 사용자 ID
     * @return 인증 여부
     */
    @Transactional(readOnly = true)
    public boolean isEmailVerified(String userId) {
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        return user.getEmailVerified() != null && user.getEmailVerified();
    }
}

