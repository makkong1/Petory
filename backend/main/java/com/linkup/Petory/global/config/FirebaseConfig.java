package com.linkup.Petory.global.config;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account.path:#{null}}")
    private Resource serviceAccountPath;

    @PostConstruct
    public void initialize() {
        if (serviceAccountPath == null || !serviceAccountPath.exists()) {
            log.warn("Firebase service-account.json 없음 — FCM 비활성화. " +
                     "firebase.service-account.path 설정 후 재시작 필요.");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try (InputStream stream = serviceAccountPath.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK 초기화 완료");
        } catch (IOException e) {
            log.error("Firebase 초기화 실패: {}", e.getMessage());
        }
    }
}
