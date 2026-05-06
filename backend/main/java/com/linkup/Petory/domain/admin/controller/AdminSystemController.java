package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminSystemFacade;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/master/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class AdminSystemController {

    private final AdminSystemFacade systemFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getSettings() {
        return ResponseEntity.ok(systemFacade.getAllConfigs());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, String> settings) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        systemFacade.upsertConfigs(settings, masterIdx);
        return ResponseEntity.ok(Map.of("message", "시스템 설정이 업데이트되었습니다.", "count", settings.size()));
    }

    @GetMapping("/settings/{key}")
    public ResponseEntity<Map<String, String>> getSetting(@PathVariable("key") String key) {
        String value = systemFacade.getConfig(key, null);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @PutMapping("/settings/{key}")
    public ResponseEntity<Void> upsertSetting(
            @PathVariable("key") String key,
            @RequestBody Map<String, String> body) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        systemFacade.upsertConfig(key, body.get("value"), body.get("description"), masterIdx);
        return ResponseEntity.noContent().build();
    }
}
