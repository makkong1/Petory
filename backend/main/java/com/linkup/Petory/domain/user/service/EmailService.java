package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 이메일 발송 서비스
 * 비동기 처리로 사용자 응답 지연 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * 이메일 인증 메일 발송 (비동기)
     * 
     * @param email   수신자 이메일
     * @param token   인증 토큰
     * @param purpose 인증 용도
     */
    @Async
    public void sendVerificationEmail(String email, String token, EmailVerificationPurpose purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject(getEmailSubject(purpose));
            message.setText(getEmailContent(token, purpose));

            mailSender.send(message);
            log.info("이메일 인증 메일 발송 완료: email={}, purpose={}", email, purpose);
        } catch (Exception e) {
            log.error("이메일 인증 메일 발송 실패: email={}, purpose={}, error={}", email, purpose, e.getMessage(), e);
        }
    }

    /**
     * 이메일 제목 생성
     */
    private String getEmailSubject(EmailVerificationPurpose purpose) {
        return switch (purpose) {
            case PASSWORD_RESET -> "[Petory] 비밀번호 변경을 위한 이메일 인증";
            case PET_CARE -> "[Petory] 펫케어 서비스 이용을 위한 이메일 인증";
            case MEETUP -> "[Petory] 모임 서비스 이용을 위한 이메일 인증";
            case LOCATION_REVIEW -> "[Petory] 리뷰 작성을 위한 이메일 인증";
            case BOARD_EDIT -> "[Petory] 게시글 수정/삭제를 위한 이메일 인증";
            case COMMENT_EDIT -> "[Petory] 댓글 수정/삭제를 위한 이메일 인증";
            case MISSING_PET -> "[Petory] 실종 제보를 위한 이메일 인증";
        };
    }

    /**
     * 이메일 내용 생성
     */
    private String getEmailContent(String token, EmailVerificationPurpose purpose) {
        // 프론트엔드 이메일 인증 페이지로 리다이렉트 (프론트엔드에서 백엔드 API 호출)
        String verificationUrl = frontendUrl + "/email-verify?token=" + token;
        String purposeDescription = getPurposeDescription(purpose);

        return String.format(
                "안녕하세요, Petory입니다.\n\n" +
                        "%s를 위해 이메일 인증이 필요합니다.\n\n" +
                        "아래 링크를 클릭하여 이메일 인증을 완료해주세요:\n" +
                        "%s\n\n" +
                        "이 링크는 24시간 동안 유효합니다.\n\n" +
                        "본인이 요청한 것이 아니라면 이 메일을 무시해주세요.\n\n" +
                        "감사합니다.\n" +
                        "Petory 팀",
                purposeDescription,
                verificationUrl);
    }

    /**
     * 용도 설명 생성
     */
    private String getPurposeDescription(EmailVerificationPurpose purpose) {
        return switch (purpose) {
            case PASSWORD_RESET -> "비밀번호 변경";
            case PET_CARE -> "펫케어 서비스 이용";
            case MEETUP -> "모임 서비스 이용";
            case LOCATION_REVIEW -> "리뷰 작성";
            case BOARD_EDIT -> "게시글 수정/삭제";
            case COMMENT_EDIT -> "댓글 수정/삭제";
            case MISSING_PET -> "실종 제보 작성";
        };
    }
}
