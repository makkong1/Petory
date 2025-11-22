package com.linkup.Petory.domain.notification.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.linkup.Petory.domain.notification.dto.NotificationDTO;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.notification.service.NotificationSseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService sseService;

    /**
     * 현재 사용자의 알림 목록 조회
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getNotifications(@RequestParam Long userId) {
        List<NotificationDTO> notifications = notificationService.getUserNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 읽지 않은 알림 목록 조회
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDTO>> getUnreadNotifications(@RequestParam Long userId) {
        List<NotificationDTO> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/unread/count")
    public ResponseEntity<Long> getUnreadCount(@RequestParam Long userId) {
        Long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * 알림 읽음 처리
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId, @RequestParam Long userId) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 모든 알림 읽음 처리
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@RequestParam Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Server-Sent Events를 통한 실시간 알림 구독
     * 
     * 참고: EventSource는 헤더에 토큰을 보낼 수 없으므로,
     * SecurityConfig에서 이 엔드포인트는 쿼리 파라미터의 토큰으로 인증하도록 설정됨
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@RequestParam Long userId) {
        log.info("SSE 연결 요청: userId={}", userId);
        SseEmitter emitter = sseService.createConnection(userId);

        // 연결 즉시 현재 읽지 않은 알림 개수 전송
        try {
            Long unreadCount = notificationService.getUnreadCount(userId);
            emitter.send(SseEmitter.event()
                    .name("unreadCount")
                    .data(unreadCount));
        } catch (Exception e) {
            log.error("초기 알림 개수 전송 실패: userId={}", userId, e);
        }

        return emitter;
    }
}
