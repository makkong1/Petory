package com.linkup.Petory.domain.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.notification.dto.FcmTokenRequestDTO;
import com.linkup.Petory.domain.notification.service.FcmService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * FCM 디바이스 토큰 등록·삭제 API. 푸시 알림 전송을 위해 사용자 기기 토큰을 관리한다.
 */
@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmService fcmService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(@Valid @RequestBody FcmTokenRequestDTO dto) {
        Long userId = authenticatedUserIdResolver.requireCurrentUserIdx();
        fcmService.saveToken(userId, dto.getToken(), dto.getDeviceType());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/token")
    public ResponseEntity<Void> removeToken(@Valid @RequestBody FcmTokenRequestDTO dto) {
        Long userId = authenticatedUserIdResolver.requireCurrentUserIdx();
        fcmService.removeToken(userId, dto.getToken());
        return ResponseEntity.ok().build();
    }
}
