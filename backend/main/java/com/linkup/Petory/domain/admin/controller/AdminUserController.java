package com.linkup.Petory.domain.admin.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.dto.UserPageResponseDTO;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.service.UsersService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 관리 컨트롤러 (관리자용)
 * - ADMIN과 MASTER 모두 접근 가능
 * - 일반 사용자(USER, SERVICE_PROVIDER) 관리
 * - ADMIN 계정 관리는 AdminUserManagementController에서 처리 (MASTER만)
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class AdminUserController {

    private final UsersService usersService;

    /**
     * 일반 사용자 목록 조회 (USER, SERVICE_PROVIDER만) - 기존 API (하위 호환성 유지)
     */
    @GetMapping
    public ResponseEntity<List<UsersDTO>> getAllUsers() {
        List<UsersDTO> users = usersService.getAllUsers();
        // ADMIN과 MASTER는 필터링하지 않고 전체 조회 가능
        return ResponseEntity.ok(users);
    }

    /**
     * 일반 사용자 목록 조회 (페이징 지원)
     */
    @GetMapping("/paging")
    public ResponseEntity<UserPageResponseDTO> getAllUsersWithPaging(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(usersService.getAllUsersWithPaging(page, size));
    }

    /**
     * 사용자 상세 조회 (관리자용)
     * - 관리자가 다른 사용자의 정보를 조회할 때 사용
     * - 일반 사용자는 /api/users/me 엔드포인트를 사용하여 자신의 정보만 조회 가능
     */
    @GetMapping("/{id}")
    public ResponseEntity<UsersDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(usersService.getUser(id));
    }

    /**
     * 사용자 생성 (일반 사용자만)
     */
    @PostMapping
    public ResponseEntity<UsersDTO> createUser(@RequestBody UsersDTO dto) {
        // ADMIN/MASTER 역할은 AdminUserManagementController에서만 생성 가능
        if (dto.getRole() != null &&
                (dto.getRole().equals("ADMIN") || dto.getRole().equals("MASTER"))) {
            throw new IllegalArgumentException("관리자 계정은 별도 엔드포인트를 사용해주세요.");
        }
        return ResponseEntity.ok(usersService.createUser(dto));
    }

    /**
     * 사용자 정보 수정
     * - ADMIN은 일반 사용자만 수정 가능
     * - MASTER는 모든 사용자 수정 가능 (단, ADMIN/MASTER 역할 변경은
     * AdminUserManagementController에서)
     */
    @PutMapping("/{id}")
    public ResponseEntity<UsersDTO> updateUser(@PathVariable Long id, @RequestBody UsersDTO dto) {
        // 역할 변경 시도 시 검증
        if (dto.getRole() != null &&
                (dto.getRole().equals("ADMIN") || dto.getRole().equals("MASTER"))) {
            throw new IllegalArgumentException("관리자 역할 변경은 별도 엔드포인트를 사용해주세요.");
        }
        return ResponseEntity.ok(usersService.updateUser(id, dto));
    }

    /**
     * 사용자 삭제 (소프트 삭제)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        // ADMIN 계정 삭제는 AdminUserManagementController에서만 가능
        var role = usersService.getRoleById(id);
        if (role.isPresent() && (role.get() == Role.ADMIN || role.get() == Role.MASTER)) {
            throw new IllegalArgumentException("관리자 계정 삭제는 별도 엔드포인트를 사용해주세요.");
        }
        usersService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 계정 복구
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<UsersDTO> restoreUser(@PathVariable Long id) {
        return ResponseEntity.ok(usersService.restoreUser(id));
    }

    /**
     * 상태 관리 (상태, 경고 횟수, 정지 기간만 업데이트)
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UsersDTO> updateUserStatus(@PathVariable Long id, @RequestBody UsersDTO dto) {
        return ResponseEntity.ok(usersService.updateUserStatus(id, dto));
    }
}

