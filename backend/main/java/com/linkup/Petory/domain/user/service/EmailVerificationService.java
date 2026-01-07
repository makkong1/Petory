package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

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
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PRE_REGISTRATION_VERIFICATION_KEY_PREFIX = "email_verification:pre_registration:";
    private static final long PRE_REGISTRATION_VERIFICATION_EXPIRE_HOURS = 24; // 24시간 유효

    /**
     * 이메일 인증 메일 발송
     * 
     * @param userId  사용자 ID
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
     * 회원가입 전 이메일 인증 메일 발송 (이메일 기반)
     * 
     * @param email 이메일 주소
     */
    public void sendPreRegistrationVerificationEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new RuntimeException("이메일을 입력해주세요.");
        }

        // 이미 해당 이메일로 가입된 사용자가 있는지 확인
        usersRepository.findByEmail(email)
                .ifPresent(existingUser -> {
                    throw new RuntimeException("이미 사용 중인 이메일입니다.");
                });

        // 인증 토큰 생성 (이메일 기반)
        String token = jwtUtil.createEmailVerificationTokenByEmail(email, EmailVerificationPurpose.REGISTRATION);

        // 이메일 발송 (비동기)
        emailService.sendVerificationEmail(email, token, EmailVerificationPurpose.REGISTRATION);

        log.info("회원가입 전 이메일 인증 메일 발송 요청: email={}", email);
    }

    /**
     * 비밀번호 재설정 이메일 발송 (이메일 기반, 인증 불필요)
     * 
     * @param email 이메일 주소
     */
    public void sendPasswordResetEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new RuntimeException("이메일을 입력해주세요.");
        }

        // 해당 이메일로 가입된 사용자가 있는지 확인
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("해당 이메일로 가입된 사용자를 찾을 수 없습니다."));

        // 인증 토큰 생성 (이메일 기반)
        String token = jwtUtil.createEmailVerificationTokenByEmail(email, EmailVerificationPurpose.PASSWORD_RESET);

        // 이메일 발송 (비동기)
        emailService.sendVerificationEmail(email, token, EmailVerificationPurpose.PASSWORD_RESET);

        log.info("비밀번호 재설정 이메일 발송 요청: email={}, userId={}", email, user.getId());
    }

    /**
     * 회원가입 전 이메일 인증 완료 처리 (Redis에 저장)
     * 
     * @param token 인증 토큰
     * @return 인증된 이메일 주소
     */
    public String verifyPreRegistrationEmail(String token) {
        // 토큰 유효성 검증
        if (!jwtUtil.validateEmailVerificationToken(token)) {
            throw new RuntimeException("유효하지 않거나 만료된 인증 토큰입니다.");
        }

        // 토큰에서 이메일 추출
        String email = jwtUtil.extractEmailFromEmailToken(token);
        if (email == null) {
            // 일반 인증 토큰인 경우
            String userId = jwtUtil.extractUserIdFromEmailToken(token);
            EmailVerificationPurpose purpose = jwtUtil.extractPurposeFromEmailToken(token);

            if (userId == null || purpose == null) {
                throw new RuntimeException("토큰에서 정보를 추출할 수 없습니다.");
            }

            // 기존 사용자 인증 처리
            Users user = usersRepository.findByIdString(userId)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

            user.setEmailVerified(true);
            usersRepository.save(user);

            log.info("이메일 인증 완료: userId={}, purpose={}", userId, purpose);
            return null; // 기존 사용자 인증이므로 이메일 반환 안 함
        }

        // 회원가입 전 인증인 경우 Redis에 저장
        String redisKey = PRE_REGISTRATION_VERIFICATION_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(
                redisKey,
                "verified",
                PRE_REGISTRATION_VERIFICATION_EXPIRE_HOURS,
                TimeUnit.HOURS);

        log.info("회원가입 전 이메일 인증 완료: email={}", email);
        return email;
    }

    /**
     * 회원가입 전 이메일 인증 완료 여부 확인
     * 
     * @param email 이메일 주소
     * @return 인증 완료 여부
     */
    public boolean isPreRegistrationEmailVerified(String email) {
        String redisKey = PRE_REGISTRATION_VERIFICATION_KEY_PREFIX + email;
        String value = stringRedisTemplate.opsForValue().get(redisKey);
        return "verified".equals(value);
    }

    /**
     * 회원가입 완료 후 Redis에서 인증 상태 삭제
     * 
     * @param email 이메일 주소
     */
    public void removePreRegistrationVerification(String email) {
        String redisKey = PRE_REGISTRATION_VERIFICATION_KEY_PREFIX + email;
        stringRedisTemplate.delete(redisKey);
        log.info("회원가입 전 이메일 인증 상태 삭제: email={}", email);
    }

    /**
     * 이메일 인증 처리
     * 
     * @param token 인증 토큰
     * @return 인증 용도 (리다이렉트용)
     */
    public EmailVerificationPurpose verifyEmail(String token) {
        // 토큰 유효성 검증
        if (!jwtUtil.validateEmailVerificationToken(token)) {
            throw new RuntimeException("유효하지 않거나 만료된 인증 토큰입니다.");
        }

        // 회원가입 전 인증인지 확인
        String email = jwtUtil.extractEmailFromEmailToken(token);
        if (email != null) {
            // 회원가입 전 인증 처리
            verifyPreRegistrationEmail(token);
            return EmailVerificationPurpose.REGISTRATION;
        }

        // 기존 사용자 인증 처리
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
     * 
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
     * 
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
