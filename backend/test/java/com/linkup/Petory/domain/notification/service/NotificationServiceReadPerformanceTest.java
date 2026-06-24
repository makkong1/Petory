package com.linkup.Petory.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.linkup.Petory.domain.notification.converter.NotificationConverter;
import com.linkup.Petory.domain.notification.dto.NotificationDTO;
import com.linkup.Petory.domain.notification.entity.Notification;
import com.linkup.Petory.domain.notification.repository.NotificationRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceReadPerformanceTest {

    private static final Long USER_ID = 100L;
    private static final int UNREAD_COUNT = 100;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private NotificationConverter notificationConverter;

    @Mock
    private RedisTemplate<String, Object> notificationRedisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private NotificationSseService sseService;

    @Mock
    private FcmService fcmService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("개선 후: 전체 읽음은 알림 개수와 무관하게 bulk UPDATE를 한 번 호출한다")
    void markAllAsRead_usesSingleBulkUpdate() {
        when(notificationRepository.markAllAsReadByUserId(USER_ID)).thenReturn(UNREAD_COUNT);

        notificationService.markAllAsRead(USER_ID);

        verify(notificationRepository).markAllAsReadByUserId(USER_ID);
        verify(usersRepository, never()).findById(USER_ID);
        verify(notificationRedisTemplate).delete("notification:" + USER_ID);
    }

    @Test
    @DisplayName("개선 후: 미읽음 개수는 Users 조회 없이 userId로 직접 COUNT한다")
    void getUnreadCount_queriesByUserIdWithoutLoadingUser() {
        when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn((long) UNREAD_COUNT);

        Long result = notificationService.getUnreadCount(USER_ID);

        assertThat(result).isEqualTo(UNREAD_COUNT);
        verify(notificationRepository).countUnreadByUserId(USER_ID);
        verify(usersRepository, never()).findById(USER_ID);
    }

    @Test
    @DisplayName("개선 후: 미읽음 목록은 Users 조회 없이 userId로 직접 조회한다")
    void getUnreadNotifications_queriesByUserIdWithoutLoadingUser() {
        Notification notification = Notification.builder().idx(1L).isRead(false).build();
        NotificationDTO dto = NotificationDTO.builder().idx(1L).isRead(false).build();
        when(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(notification));
        when(notificationConverter.toDTO(notification)).thenReturn(dto);

        List<NotificationDTO> result = notificationService.getUnreadNotifications(USER_ID);

        assertThat(result).containsExactly(dto);
        verify(notificationRepository).findByUserIdAndIsReadFalseOrderByCreatedAtDesc(USER_ID);
        verify(usersRepository, never()).findById(USER_ID);
    }

    @Test
    @DisplayName("개선 후: 전체 목록은 Users 조회 없이 userId로 직접 조회한다")
    void getUserNotifications_queriesByUserIdWithoutLoadingUser() {
        Notification notification = Notification.builder().idx(1L).isRead(false).build();
        NotificationDTO dto = NotificationDTO.builder().idx(1L).isRead(false).build();
        when(notificationRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("notification:" + USER_ID)).thenReturn(null);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(notification));
        when(notificationConverter.toDTO(notification)).thenReturn(dto);

        List<NotificationDTO> result = notificationService.getUserNotifications(USER_ID);

        assertThat(result).containsExactly(dto);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(USER_ID);
        verify(usersRepository, never()).findById(USER_ID);
    }
}
