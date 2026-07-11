package com.linkup.Petory.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.admin.service.AdminUserFacade;
import com.linkup.Petory.domain.user.dto.AdminUserPageResponseDTO;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
/**
 * 관리자용 일반 사용자 목록 조회·상태 변경·삭제 API. [ADMIN, MASTER]
 */
public class AdminUserController {

    private final AdminUserFacade adminUserFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping("/paging")
    public ResponseEntity<AdminUserPageResponseDTO> getUsers(
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserFacade.getUsers(role, status, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsersDTO> getUser(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminUserFacade.getUser(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UsersDTO> updateStatus(@PathVariable("id") Long id, @RequestBody UsersDTO dto) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminUserFacade.updateStatus(id, dto, adminIdx));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        adminUserFacade.deleteUser(id, adminIdx);
        return ResponseEntity.noContent().build();
    }
}
