package com.linkup.Petory.domain.user.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.care.service.CareReviewService;
import com.linkup.Petory.domain.location.service.LocationServiceReviewService;
import com.linkup.Petory.domain.meetup.dto.MeetupHistoryDTO;
import com.linkup.Petory.domain.meetup.service.MeetupService;
import com.linkup.Petory.domain.user.dto.UserProfileWithReviewsDTO;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.exception.InvalidPasswordException;
import com.linkup.Petory.domain.user.exception.UserValidationException;
import com.linkup.Petory.global.security.CustomUserDetails;
import com.linkup.Petory.domain.user.service.EmailVerificationService;
import com.linkup.Petory.domain.user.service.UsersService;
import com.linkup.Petory.util.JwtUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 일반 사용자용 프로필 관리 컨트롤러
 * - 모든 인증된 사용자(USER, SERVICE_PROVIDER, ADMIN, MASTER)가 자신의 프로필을 조회/수정 가능
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UsersService usersService;
    private final CareReviewService careReviewService;
    private final LocationServiceReviewService locationServiceReviewService;
    private final MeetupService meetupService;
    private final EmailVerificationService emailVerificationService;
    private final JwtUtil jwtUtil;

    private boolean isServiceProvider(UsersDTO user) {
        return "SERVICE_PROVIDER".equals(user.getRole());
    }

    private long countLikedMeetups(List<MeetupHistoryDTO> histories) {
        return histories.stream()
                .filter(history -> Boolean.TRUE.equals(history.getLiked()))
                .count();
    }

    /**
     * 자신의 프로필 조회 (리뷰 포함)
     * [리팩토링] getReviewsByReviewee + getAverageRating 2회 → getReviewsWithAverage 1회
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileWithReviewsDTO> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String userId = userDetails.getLoginId();
        UsersDTO user = usersService.getMyProfile(userId);

        Long userIdx = user.getIdx();
        boolean serviceProvider = isServiceProvider(user);
        var careReviewSummary = serviceProvider
                ? careReviewService.getReviewsWithAverage(userIdx)
                : careReviewService.getWrittenReviewsWithAverage(userIdx);
        int completedCareCount = serviceProvider ? careReviewService.getCompletedCareCount(userIdx) : 0;
        var locationServiceReviewSummary = locationServiceReviewService.getReviewsWithAverage(userIdx);
        List<MeetupHistoryDTO> meetupHistories = meetupService.getMeetupHistory(userIdx);

        return ResponseEntity.ok(UserProfileWithReviewsDTO.builder()
                .user(user)
                .reviews(careReviewSummary.getReviews())
                .careReviewMode(serviceProvider ? "RECEIVED" : "WRITTEN")
                .locationServiceReviews(locationServiceReviewSummary.getReviews())
                .averageRating(careReviewSummary.getAverageRating())
                .locationServiceAverageRating(locationServiceReviewSummary.getAverageRating())
                .reviewCount(careReviewSummary.getReviewCount())
                .completedCareCount(completedCareCount)
                .locationServiceReviewCount(locationServiceReviewSummary.getReviewCount())
                .meetupHistories(meetupHistories)
                .meetupHistoryCount(meetupHistories.size())
                .meetupLikedCount((int) countLikedMeetups(meetupHistories))
                .build());
    }

    /**
     * 자신의 프로필 수정 (닉네임, 이메일, 전화번호, 위치, 펫 정보 등)
     * - 당사자만 수정 가능 (권한 검증 포함)
     * - 관리자(MASTER 포함)도 다른 사람의 프로필을 수정할 수 없음
     */
    @PutMapping("/me")
    public ResponseEntity<UsersDTO> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UsersDTO dto) {
        String userId = userDetails.getLoginId();
        UsersDTO updated = usersService.updateMyProfile(userId, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * 비밀번호 변경
     */
    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String userId = userDetails.getLoginId();
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        if (currentPassword == null || newPassword == null) {
            throw InvalidPasswordException.bothRequired();
        }

        usersService.changePassword(userId, currentPassword, newPassword);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 성공적으로 변경되었습니다."));
    }

    /**
     * 닉네임 변경
     */
    @PatchMapping("/me/username")
    public ResponseEntity<UsersDTO> updateMyUsername(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String userId = userDetails.getLoginId();
        String newUsername = request.get("username");

        if (newUsername == null || newUsername.isEmpty()) {
            throw UserValidationException.nicknameRequired();
        }

        UsersDTO updated = usersService.updateMyUsername(userId, newUsername);
        return ResponseEntity.ok(updated);
    }

    /**
     * 닉네임 설정 (소셜 로그인 사용자용)
     */
    @PostMapping("/me/nickname")
    public ResponseEntity<UsersDTO> setNickname(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String userId = userDetails.getLoginId();
        String nickname = request.get("nickname");

        if (nickname == null || nickname.trim().isEmpty()) {
            throw UserValidationException.nicknameRequired();
        }

        UsersDTO updated = usersService.setNickname(userId, nickname);
        return ResponseEntity.ok(updated);
    }

    /**
     * 닉네임 중복 검사
     */
    @GetMapping("/nickname/check")
    public ResponseEntity<Map<String, Object>> checkNicknameAvailability(@RequestParam("nickname") String nickname) {
        boolean available = usersService.checkNicknameAvailability(nickname);
        return ResponseEntity.ok(Map.of(
                "available", available,
                "message", available ? "사용 가능한 닉네임입니다." : "이미 사용 중인 닉네임입니다."));
    }

    /**
     * 아이디 중복 검사
     */
    @GetMapping("/id/check")
    public ResponseEntity<Map<String, Object>> checkIdAvailability(@RequestParam("id") String id) {
        boolean available = usersService.checkIdAvailability(id);
        return ResponseEntity.ok(Map.of(
                "available", available,
                "message", available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다."));
    }

    /**
     * 이메일 인증 메일 발송
     */
    @PostMapping("/email/verify")
    public ResponseEntity<Map<String, Object>> sendVerificationEmail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String userId = userDetails.getLoginId();
        String purposeStr = request.get("purpose");

        if (purposeStr == null || purposeStr.isEmpty()) {
            throw UserValidationException.purposeRequired();
        }

        EmailVerificationPurpose purpose;
        try {
            purpose = EmailVerificationPurpose.valueOf(purposeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw UserValidationException.invalidPurpose(purposeStr);
        }
        emailVerificationService.sendVerificationEmail(userId, purpose);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "이메일 인증 메일이 발송되었습니다."));
    }

    /**
     * 회원가입 전 이메일 인증 메일 발송 (인증 불필요)
     */
    @PostMapping("/email/verify/pre-registration")
    public ResponseEntity<Map<String, Object>> sendPreRegistrationVerificationEmail(
            @RequestBody Map<String, String> request) {
        String email = request.get("email");

        if (email == null || email.isEmpty()) {
            throw UserValidationException.emailRequired();
        }

        emailVerificationService.sendPreRegistrationVerificationEmail(email);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "이메일 인증 메일이 발송되었습니다. 이메일을 확인해주세요."));
    }

    /**
     * 회원가입 전 이메일 인증 완료 여부 확인 (인증 불필요)
     */
    @GetMapping("/email/verify/pre-registration/check")
    public ResponseEntity<Map<String, Object>> checkPreRegistrationVerification(@RequestParam("email") String email) {
        boolean verified = emailVerificationService.isPreRegistrationEmailVerified(email);

        return ResponseEntity.ok(Map.of(
                "verified", verified,
                "message", verified ? "이메일 인증이 완료되었습니다." : "이메일 인증이 완료되지 않았습니다."));
    }

    /**
     * 이메일 인증 처리
     */
    @GetMapping("/email/verify/{token}")
    public ResponseEntity<Map<String, Object>> verifyEmail(@PathVariable("token") String token) {
        EmailVerificationPurpose purpose = emailVerificationService.verifyEmail(token);

        // 회원가입 전 인증인 경우 이메일 추출
        String email = jwtUtil.extractEmailFromEmailToken(token);
        if (email != null && purpose == EmailVerificationPurpose.REGISTRATION) {
            // 회원가입 페이지로 리다이렉트 (이메일 인증 완료 상태로)
            String redirectUrl = "/register?emailVerified=true&email=" + email;
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "이메일 인증이 완료되었습니다. 회원가입을 진행해주세요.",
                    "purpose", purpose.name(),
                    "email", email,
                    "redirectUrl", redirectUrl));
        }

        // 용도에 따른 리다이렉트 URL 생성
        String redirectUrl = getRedirectUrl(purpose);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "이메일 인증이 완료되었습니다.",
                "purpose", purpose.name(),
                "redirectUrl", redirectUrl));
    }

    /**
     * 용도에 따른 리다이렉트 URL 생성
     */
    private String getRedirectUrl(EmailVerificationPurpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> "/"; // 회원가입 완료 후 메인 페이지
            case PASSWORD_RESET -> "/password-reset";
            case PET_CARE -> "/care-requests";
            case MEETUP -> "/meetups";
            case LOCATION_REVIEW -> "/location-services";
            case BOARD_EDIT -> "/boards";
            case COMMENT_EDIT -> "/boards";
            case MISSING_PET -> "/missing-pets";
        };
    }

    /**
     * 다른 사용자의 프로필 조회 (리뷰 포함)
     * [리팩토링] getReviewsByReviewee + getAverageRating 2회 → getReviewsWithAverage 1회
     * - 인증된 사용자는 다른 사용자의 프로필을 조회할 수 있음
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileWithReviewsDTO> getUserProfile(@PathVariable("userId") Long userId) {
        UsersDTO user = usersService.getUser(userId);
        boolean serviceProvider = isServiceProvider(user);
        var careReviewSummary = serviceProvider
                ? careReviewService.getReviewsWithAverage(userId)
                : careReviewService.getWrittenReviewsWithAverage(userId);
        int completedCareCount = serviceProvider ? careReviewService.getCompletedCareCount(userId) : 0;
        var locationServiceReviewSummary = locationServiceReviewService.getReviewsWithAverage(userId);
        List<MeetupHistoryDTO> meetupHistories = meetupService.getMeetupHistory(userId);

        UserProfileWithReviewsDTO profile = UserProfileWithReviewsDTO.builder()
                .user(user)
                .reviews(careReviewSummary.getReviews())
                .careReviewMode(serviceProvider ? "RECEIVED" : "WRITTEN")
                .locationServiceReviews(locationServiceReviewSummary.getReviews())
                .averageRating(careReviewSummary.getAverageRating())
                .locationServiceAverageRating(locationServiceReviewSummary.getAverageRating())
                .reviewCount(careReviewSummary.getReviewCount())
                .completedCareCount(completedCareCount)
                .locationServiceReviewCount(locationServiceReviewSummary.getReviewCount())
                .meetupHistories(meetupHistories)
                .meetupHistoryCount(meetupHistories.size())
                .meetupLikedCount((int) countLikedMeetups(meetupHistories))
                .build();

        return ResponseEntity.ok(profile);
    }

    /**
     * 특정 사용자의 리뷰 목록 조회
     */
    @GetMapping("/{userId}/reviews")
    public ResponseEntity<List<CareReviewDTO>> getUserReviews(@PathVariable("userId") Long userId) {
        List<CareReviewDTO> reviews = careReviewService.getReviewsByReviewee(userId);
        return ResponseEntity.ok(reviews);
    }
}
