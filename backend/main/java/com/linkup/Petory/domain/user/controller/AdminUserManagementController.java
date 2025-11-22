package com.linkup.Petory.domain.user.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.user.converter.UsersConverter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MASTER 전용: ADMIN 계정 관리 컨트롤러
 * - ADMIN 계정 생성/삭제/권한 변경
 * - 일반 사용자를 ADMIN으로 승격 가능
 * - MASTER 권한 변경 불가 (MASTER는 변경 불가)
 * - 일반 사용자 관리는 UsersController에서 처리 (ADMIN/MASTER 모두 가능)
 */
@RestController
@RequestMapping("/api/master/admin-users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
@Slf4j
public class AdminUserManagementController {

    private final UsersRepository usersRepository;
    private final UsersConverter usersConverter;
    private final PasswordEncoder passwordEncoder;

    /**
     * ADMIN 계정 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<UsersDTO>> getAdminUsers() {
        List<Users> adminUsers = usersRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN || user.getRole() == Role.MASTER)
                .collect(Collectors.toList());
        return ResponseEntity.ok(usersConverter.toDTOList(adminUsers));
    }

    /**
     * ADMIN 계정 생성
     */
    @PostMapping
    public ResponseEntity<UsersDTO> createAdminUser(@RequestBody UsersDTO dto) {
        // ADMIN 역할만 허용 (MASTER는 생성 불가)
        if (dto.getRole() == null || !dto.getRole().equals("ADMIN")) {
            throw new IllegalArgumentException("ADMIN 역할만 지정할 수 있습니다.");
        }

        // 중복 체크
        if (usersRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다.");
        }
        if (usersRepository.findById(dto.getId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }
        if (dto.getEmail() != null && usersRepository.findAll().stream()
                .anyMatch(u -> u.getEmail() != null && u.getEmail().equals(dto.getEmail()))) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        Users user = usersConverter.toEntity(dto);
        user.setRole(Role.valueOf(dto.getRole()));

        // 비밀번호 암호화
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        } else {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }

        Users saved = usersRepository.save(user);
        log.info("MASTER가 ADMIN 계정 생성: username={}, role={}", saved.getUsername(), saved.getRole());
        return ResponseEntity.ok(usersConverter.toDTO(saved));
    }

    /**
     * 일반 사용자를 ADMIN으로 승격
     * - 일반 사용자(USER, SERVICE_PROVIDER)를 ADMIN으로 승격 가능
     * - 이미 ADMIN인 경우 변경 불필요
     * - MASTER로 변경 불가능 (MASTER는 변경 불가)
     */
    @PatchMapping("/{id}/promote-to-admin")
    public ResponseEntity<UsersDTO> promoteToAdmin(@PathVariable Long id) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // MASTER는 변경 불가
        if (user.getRole() == Role.MASTER) {
            throw new IllegalArgumentException("MASTER 권한은 변경할 수 없습니다.");
        }

        // 이미 ADMIN인 경우
        if (user.getRole() == Role.ADMIN) {
            log.info("이미 ADMIN 권한을 가진 사용자: userId={}", id);
            return ResponseEntity.ok(usersConverter.toDTO(user));
        }

        // 일반 사용자를 ADMIN으로 승격
        user.setRole(Role.ADMIN);
        Users updated = usersRepository.save(user);
        log.info("MASTER가 일반 사용자를 ADMIN으로 승격: userId={}, username={}", id, updated.getUsername());
        return ResponseEntity.ok(usersConverter.toDTO(updated));
    }

    /**
     * ADMIN 계정 권한 변경 (ADMIN만 가능, MASTER로 변경 불가)
     * - ADMIN 계정의 다른 정보만 변경 가능
     * - 역할 변경은 promoteToAdmin 엔드포인트 사용
     */
    @PatchMapping("/{id}/role")
    public ResponseEntity<UsersDTO> changeAdminRole(
            @PathVariable Long id,
            @RequestBody RoleChangeRequest request) {
        
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // MASTER는 변경 불가
        if (user.getRole() == Role.MASTER) {
            throw new IllegalArgumentException("MASTER 권한은 변경할 수 없습니다.");
        }

        // ADMIN만 변경 가능
        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("일반 사용자는 promote-to-admin 엔드포인트를 사용해주세요.");
        }

        // 요청된 역할 검증 - ADMIN만 허용
        if (request.getRole() == null || !request.getRole().equals("ADMIN")) {
            throw new IllegalArgumentException("ADMIN 역할만 지정할 수 있습니다. MASTER로 변경할 수 없습니다.");
        }

        // 이미 ADMIN이므로 변경 불필요
        log.info("ADMIN 권한 변경 요청 (변경 불필요): userId={}", id);
        return ResponseEntity.ok(usersConverter.toDTO(user));
    }

    /**
     * ADMIN 계정 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdminUser(@PathVariable Long id) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // ADMIN 또는 MASTER만 삭제 가능
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.MASTER) {
            throw new IllegalArgumentException("일반 사용자는 이 엔드포인트로 관리할 수 없습니다.");
        }

        usersRepository.deleteById(id);
        log.warn("MASTER가 ADMIN 계정 삭제: userId={}, username={}", id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * ADMIN 계정 비밀번호 변경
     */
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> changeAdminPassword(
            @PathVariable Long id,
            @RequestBody PasswordChangeRequest request) {
        
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // ADMIN 또는 MASTER만 변경 가능
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.MASTER) {
            throw new IllegalArgumentException("일반 사용자는 이 엔드포인트로 관리할 수 없습니다.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isEmpty()) {
            throw new IllegalArgumentException("새 비밀번호는 필수입니다.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        usersRepository.save(user);
        log.info("MASTER가 ADMIN 계정 비밀번호 변경: userId={}", id);
        return ResponseEntity.noContent().build();
    }

    // DTO 클래스들
    public static class RoleChangeRequest {
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class PasswordChangeRequest {
        private String newPassword;

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }
}

