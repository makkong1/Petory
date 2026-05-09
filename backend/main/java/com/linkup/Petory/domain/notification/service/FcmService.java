package com.linkup.Petory.domain.notification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.linkup.Petory.domain.notification.entity.FcmToken;
import com.linkup.Petory.domain.notification.repository.FcmTokenRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UsersRepository usersRepository;

    @Transactional
    public void saveToken(Long userId, String token, FcmToken.DeviceType deviceType) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        fcmTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> {
                    if (!existing.getUser().getIdx().equals(userId)) {
                        // 다른 사용자 소유 토큰 → 기존 삭제 후 새 사용자에게 귀속
                        fcmTokenRepository.delete(existing);
                        fcmTokenRepository.save(FcmToken.builder()
                                .user(user).token(token).deviceType(deviceType).build());
                    }
                },
                () -> fcmTokenRepository.save(FcmToken.builder()
                        .user(user).token(token).deviceType(deviceType).build())
        );
    }

    @Transactional
    public void removeToken(Long userId, String token) {
        fcmTokenRepository.findByToken(token).ifPresent(fcmToken -> {
            if (fcmToken.getUser().getIdx().equals(userId)) {
                fcmTokenRepository.delete(fcmToken);
            }
        });
    }

    @Transactional
    public void removeAllTokens(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        fcmTokenRepository.deleteByUser(user);
    }

    /**
     * 사용자의 모든 기기에 FCM 푸시 알림 발송.
     * Firebase가 초기화되지 않은 경우(설정 누락) 조용히 건너뜀.
     */
    public void sendToUser(Long userId, String title, String body) {
        if (FirebaseApp.getApps().isEmpty()) {
            return;
        }
        Users user = usersRepository.findById(userId).orElse(null);
        if (user == null) return;

        List<FcmToken> tokens = fcmTokenRepository.findByUser(user);
        if (tokens.isEmpty()) return;

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        for (FcmToken fcmToken : tokens) {
            Message message = Message.builder()
                    .setToken(fcmToken.getToken())
                    .setNotification(notification)
                    .build();
            try {
                FirebaseMessaging.getInstance().send(message);
                log.debug("FCM 발송 완료: userId={}, deviceType={}", userId, fcmToken.getDeviceType());
            } catch (FirebaseMessagingException e) {
                log.warn("FCM 발송 실패: userId={}, error={}", userId, e.getMessage());
                MessagingErrorCode errorCode = e.getMessagingErrorCode();
                if (errorCode == MessagingErrorCode.UNREGISTERED
                        || errorCode == MessagingErrorCode.INVALID_ARGUMENT
                        || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH) {
                    fcmTokenRepository.deleteByToken(fcmToken.getToken());
                }
            }
        }
    }
}
