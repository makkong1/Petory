package com.linkup.Petory.domain.notification.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.linkup.Petory.domain.notification.dto.NotificationDTO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationSseService {

    // 사용자별 SSE 연결 관리
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 사용자에게 SSE 연결 생성
     */
    public SseEmitter createConnection(Long userId) {
        SseEmitter emitter = new SseEmitter(3600000L); // 1시간 타임아웃
        
        emitter.onCompletion(() -> {
            log.info("SSE 연결 완료: userId={}", userId);
            emitters.remove(userId);
        });
        
        emitter.onTimeout(() -> {
            log.info("SSE 연결 타임아웃: userId={}", userId);
            emitters.remove(userId);
        });
        
        emitter.onError((ex) -> {
            log.error("SSE 연결 오류: userId={}, error={}", userId, ex.getMessage());
            emitters.remove(userId);
        });

        emitters.put(userId, emitter);
        log.info("SSE 연결 생성: userId={}, 현재 연결 수={}", userId, emitters.size());
        
        return emitter;
    }

    /**
     * 특정 사용자에게 알림 전송
     */
    public void sendNotification(Long userId, NotificationDTO notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(notification));
                log.info("알림 전송 성공: userId={}, notificationId={}", userId, notification.getIdx());
            } catch (IOException e) {
                log.error("알림 전송 실패: userId={}, error={}", userId, e.getMessage());
                emitters.remove(userId);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("emitter 완료 처리 실패", ex);
                }
            }
        } else {
            log.debug("SSE 연결 없음: userId={} (알림은 DB/Redis에 저장됨)", userId);
        }
    }

    /**
     * 사용자 연결 해제
     */
    public void removeConnection(Long userId) {
        SseEmitter emitter = emitters.remove(userId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.error("emitter 완료 처리 실패: userId={}", userId, e);
            }
        }
    }

    /**
     * 연결된 사용자 수 조회
     */
    public int getConnectedUserCount() {
        return emitters.size();
    }
}

