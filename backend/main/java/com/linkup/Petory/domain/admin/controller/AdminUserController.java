package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminUserFacade;
import com.linkup.Petory.domain.user.dto.UserPageResponseDTO;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class AdminUserController {

    private final AdminUserFacade adminUserFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping("/paging")
    public ResponseEntity<UserPageResponseDTO> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
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

    @PostMapping("/{id}/restore")
    public ResponseEntity<UsersDTO> restoreUser(@PathVariable("id") Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminUserFacade.restoreUser(id, adminIdx));
    }
}
