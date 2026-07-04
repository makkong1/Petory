package com.linkup.Petory.domain.notification.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.notification.dto.NotificationDTO;
import com.linkup.Petory.domain.notification.service.FcmService;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.notification.service.NotificationSseService;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationSseService sseService;

    @Mock
    private FcmService fcmService;

    @InjectMocks
    private NotificationDispatchListener listener;

    private NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(1L, new NotificationDTO(), "제목", "내용");
    }

    @Test
    @DisplayName("정상: 이벤트 수신 시 Redis 캐시/SSE/FCM 세 채널 모두 발송한다")
    void 정상_세_채널_모두_발송() {
        listener.onNotificationCreated(event());

        verify(notificationService).cacheToRedis(anyLong(), any(NotificationDTO.class));
        verify(sseService).sendNotification(anyLong(), any(NotificationDTO.class));
        verify(fcmService).sendToUser(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("예외: Redis 캐시가 실패해도 SSE와 FCM 발송은 계속된다")
    void 예외_레디스_실패해도_나머지_채널_발송() {
        doThrow(new RuntimeException("Redis down"))
                .when(notificationService).cacheToRedis(anyLong(), any(NotificationDTO.class));

        listener.onNotificationCreated(event());

        verify(sseService).sendNotification(anyLong(), any(NotificationDTO.class));
        verify(fcmService).sendToUser(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("예외: SSE 전송이 실패해도 FCM 발송은 계속된다")
    void 예외_SSE_실패해도_FCM_발송() {
        doThrow(new RuntimeException("SSE broken pipe"))
                .when(sseService).sendNotification(anyLong(), any(NotificationDTO.class));

        listener.onNotificationCreated(event());

        verify(fcmService).sendToUser(anyLong(), anyString(), anyString());
    }
}
