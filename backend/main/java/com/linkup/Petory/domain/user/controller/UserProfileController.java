package com.linkup.Petory.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.service.UsersService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
}

