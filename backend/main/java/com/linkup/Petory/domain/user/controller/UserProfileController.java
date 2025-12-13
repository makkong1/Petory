package com.linkup.Petory.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.care.service.CareReviewService;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.dto.UserProfileWithReviewsDTO;
import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.service.EmailVerificationService;
import com.linkup.Petory.domain.user.service.UsersService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

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
    private final EmailVerificationService emailVerificationService;

    /**
     * 현재 로그인한 사용자의 ID 추출
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("인증되지 않은 사용자입니다.");
        }
        // UserDetails의 username이 실제로는 userId (id 필드)
        return authentication.getName();
    }

    /**
     * 자신의 프로필 조회
     */
    @GetMapping("/me")
    public ResponseEntity<UsersDTO> getMyProfile() {
        String userId = getCurrentUserId();
        UsersDTO profile = usersService.getMyProfile(userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * 자신의 프로필 수정 (닉네임, 이메일, 전화번호, 위치, 펫 정보 등)
     */
    @PutMapping("/me")
    public ResponseEntity<UsersDTO> updateMyProfile(@RequestBody UsersDTO dto) {
        String userId = getCurrentUserId();
        UsersDTO updated = usersService.updateMyProfile(userId, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * 비밀번호 변경
     */
    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody Map<String, String> request) {
        String userId = getCurrentUserId();
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        if (currentPassword == null || newPassword == null) {
            throw new IllegalArgumentException("현재 비밀번호와 새 비밀번호를 모두 입력해주세요.");
        }

        usersService.changePassword(userId, currentPassword, newPassword);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 성공적으로 변경되었습니다."));
    }

    /**
     * 닉네임 변경
     */
    @PatchMapping("/me/username")
    public ResponseEntity<UsersDTO> updateMyUsername(@RequestBody Map<String, String> request) {
        String userId = getCurrentUserId();
        String newUsername = request.get("username");

        if (newUsername == null || newUsername.isEmpty()) {
            throw new IllegalArgumentException("닉네임을 입력해주세요.");
        }

        UsersDTO updated = usersService.updateMyUsername(userId, newUsername);
        return ResponseEntity.ok(updated);
    }

    /**
     * 닉네임 설정 (소셜 로그인 사용자용)
     */
    @PostMapping("/me/nickname")
    public ResponseEntity<UsersDTO> setNickname(@RequestBody Map<String, String> request) {
        String userId = getCurrentUserId();
        String nickname = request.get("nickname");

        if (nickname == null || nickname.trim().isEmpty()) {
            throw new IllegalArgumentException("닉네임을 입력해주세요.");
        }

        UsersDTO updated = usersService.setNickname(userId, nickname);
        return ResponseEntity.ok(updated);
    }

    /**
     * 닉네임 중복 검사
     */
    @GetMapping("/nickname/check")
    public ResponseEntity<Map<String, Object>> checkNicknameAvailability(@RequestParam String nickname) {
        boolean available = usersService.checkNicknameAvailability(nickname);
        return ResponseEntity.ok(Map.of(
            "available", available,
            "message", available ? "사용 가능한 닉네임입니다." : "이미 사용 중인 닉네임입니다."
        ));
    }

    /**
     * 아이디 중복 검사
     */
    @GetMapping("/id/check")
    public ResponseEntity<Map<String, Object>> checkIdAvailability(@RequestParam String id) {
        boolean available = usersService.checkIdAvailability(id);
        return ResponseEntity.ok(Map.of(
            "available", available,
            "message", available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다."
        ));
    }

    /**
     * 이메일 인증 메일 발송
     */
    @PostMapping("/email/verify")
    public ResponseEntity<Map<String, Object>> sendVerificationEmail(@RequestBody Map<String, String> request) {
        String userId = getCurrentUserId();
        String purposeStr = request.get("purpose");
        
        if (purposeStr == null || purposeStr.isEmpty()) {
            throw new IllegalArgumentException("인증 용도(purpose)를 지정해주세요.");
        }

        try {
            EmailVerificationPurpose purpose = EmailVerificationPurpose.valueOf(purposeStr.toUpperCase());
            emailVerificationService.sendVerificationEmail(userId, purpose);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "이메일 인증 메일이 발송되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 인증 용도입니다: " + purposeStr);
        }
    }

    /**
     * 이메일 인증 처리
     */
    @GetMapping("/email/verify/{token}")
    public ResponseEntity<Map<String, Object>> verifyEmail(@PathVariable String token) {
        try {
            EmailVerificationPurpose purpose = emailVerificationService.verifyEmail(token);
            
            // 용도에 따른 리다이렉트 URL 생성
            String redirectUrl = getRedirectUrl(purpose);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "이메일 인증이 완료되었습니다.",
                "purpose", purpose.name(),
                "redirectUrl", redirectUrl
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 용도에 따른 리다이렉트 URL 생성
     */
    private String getRedirectUrl(EmailVerificationPurpose purpose) {
        return switch (purpose) {
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
     * - 인증된 사용자는 다른 사용자의 프로필을 조회할 수 있음
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileWithReviewsDTO> getUserProfile(@PathVariable Long userId) {
        UsersDTO user = usersService.getUser(userId);
        List<CareReviewDTO> reviews = careReviewService.getReviewsByReviewee(userId);
        Double averageRating = careReviewService.getAverageRating(userId);
        
        UserProfileWithReviewsDTO profile = UserProfileWithReviewsDTO.builder()
            .user(user)
            .reviews(reviews)
            .averageRating(averageRating)
            .reviewCount(reviews.size())
            .build();
        
        return ResponseEntity.ok(profile);
    }

    /**
     * 특정 사용자의 리뷰 목록 조회
     */
    @GetMapping("/{userId}/reviews")
    public ResponseEntity<List<CareReviewDTO>> getUserReviews(@PathVariable Long userId) {
        List<CareReviewDTO> reviews = careReviewService.getReviewsByReviewee(userId);
        return ResponseEntity.ok(reviews);
    }
}

