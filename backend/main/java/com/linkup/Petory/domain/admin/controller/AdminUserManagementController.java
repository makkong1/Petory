package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminUserFacade;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master/admin-users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class AdminUserManagementController {

    private final AdminUserFacade adminUserFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<List<UsersDTO>> getAdminUsers() {
        return ResponseEntity.ok(adminUserFacade.getAdminUsers());
    }

    @PostMapping
    public ResponseEntity<UsersDTO> createAdminUser(@RequestBody UsersDTO dto) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminUserFacade.createAdminUser(dto, masterIdx));
    }

    @PatchMapping("/{id}/promote-to-admin")
    public ResponseEntity<UsersDTO> promoteToAdmin(@PathVariable Long id) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminUserFacade.promoteToAdmin(id, masterIdx));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdminUser(@PathVariable Long id) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        adminUserFacade.deleteAdminUser(id, masterIdx);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> changeAdminPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        adminUserFacade.changeAdminPassword(id, body.get("newPassword"), masterIdx);
        return ResponseEntity.noContent().build();
    }
}
