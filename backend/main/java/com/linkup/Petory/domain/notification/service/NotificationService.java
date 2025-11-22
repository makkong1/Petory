package com.linkup.Petory.domain.notification.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.notification.converter.NotificationConverter;
import com.linkup.Petory.domain.notification.dto.NotificationDTO;
import com.linkup.Petory.domain.notification.entity.Notification;
import com.linkup.Petory.domain.notification.entity.NotificationType;
import com.linkup.Petory.domain.notification.repository.NotificationRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UsersRepository usersRepository;
    private final NotificationConverter notificationConverter;
    private final RedisTemplate<String, Object> notificationRedisTemplate;

    private static final String REDIS_KEY_PREFIX = "notification:";
    private static final int REDIS_TTL_HOURS = 24; // Redis에 24시간 저장

    /**
     * 알림 생성 및 발송
     * 
     * @param userId      알림을 받을 사용자 ID
     * @param type        알림 타입
     * @param title       알림 제목
     * @param content     알림 내용
     * @param relatedId   관련 게시글/댓글 ID
     * @param relatedType 관련 타입
     */
    @Transactional
    public NotificationDTO createNotification(Long userId, NotificationType type, String title, String content,
            Long relatedId, String relatedType) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationDTO dto = notificationConverter.toDTO(saved);

        // Redis에 실시간 알림 저장 (최신 알림 목록 관리)
        saveToRedis(userId, dto);

        return dto;
    }

    /**
     * 사용자의 알림 목록 조회
     */
    public List<NotificationDTO> getUserNotifications(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Redis에서 먼저 조회 시도
        List<NotificationDTO> redisNotifications = getFromRedis(userId);
        if (redisNotifications != null && !redisNotifications.isEmpty()) {
            // Redis에 데이터가 있으면 DB와 병합하여 반환
            List<NotificationDTO> dbNotifications = notificationRepository.findByUserOrderByCreatedAtDesc(user)
                    .stream()
                    .map(notificationConverter::toDTO)
                    .collect(Collectors.toList());

            // Redis와 DB 데이터 병합 (중복 제거)
            return mergeNotifications(redisNotifications, dbNotifications);
        }

        // Redis에 없으면 DB에서 조회
        return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(notificationConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 목록 조회
     */
    public List<NotificationDTO> getUnreadNotifications(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user)
                .stream()
                .map(notificationConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    public Long getUnreadCount(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return notificationRepository.countUnreadByUser(user);
    }

    /**
     * 알림 읽음 처리
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        // 본인의 알림만 읽음 처리 가능
        if (!notification.getUser().getIdx().equals(userId)) {
            throw new IllegalStateException("본인의 알림만 읽음 처리할 수 있습니다.");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);

        // Redis에서도 제거
        removeFromRedis(userId, notificationId);
    }

    /**
     * 모든 알림 읽음 처리
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Notification> unreadNotifications = notificationRepository
                .findByUserAndIsReadFalseOrderByCreatedAtDesc(user);

        unreadNotifications.forEach(notification -> notification.setIsRead(true));
        notificationRepository.saveAll(unreadNotifications);

        // Redis에서 해당 사용자의 모든 알림 제거
        String redisKey = REDIS_KEY_PREFIX + userId;
        notificationRedisTemplate.delete(redisKey);
    }

    /**
     * Redis에 알림 저장
     */
    private void saveToRedis(Long userId, NotificationDTO notification) {
        String redisKey = REDIS_KEY_PREFIX + userId;
        List<NotificationDTO> existingNotifications = getFromRedis(userId);

        // 수정 가능한 리스트 생성
        List<NotificationDTO> notifications;
        if (existingNotifications == null || existingNotifications.isEmpty()) {
            notifications = new java.util.ArrayList<>();
        } else {
            // 기존 리스트를 수정 가능한 새 리스트로 복사 (불변 리스트일 수 있으므로)
            notifications = new java.util.ArrayList<>(existingNotifications);
        }

        // 최신 알림을 맨 앞에 추가 (최대 50개만 유지)
        notifications.add(0, notification);
        if (notifications.size() > 50) {
            notifications = notifications.subList(0, 50);
            // subList는 원본 리스트의 뷰이므로 새 리스트로 복사 필요
            notifications = new java.util.ArrayList<>(notifications);
        }

        notificationRedisTemplate.opsForValue().set(redisKey, notifications,
                java.time.Duration.ofHours(REDIS_TTL_HOURS));
    }

    /**
     * Redis에서 알림 조회
     */
    @SuppressWarnings("unchecked")
    private List<NotificationDTO> getFromRedis(Long userId) {
        String redisKey = REDIS_KEY_PREFIX + userId;
        Object value = notificationRedisTemplate.opsForValue().get(redisKey);

        if (value instanceof List) {
            return (List<NotificationDTO>) value;
        }
        return null;
    }

    /**
     * Redis에서 알림 제거
     */
    private void removeFromRedis(Long userId, Long notificationId) {
        String redisKey = REDIS_KEY_PREFIX + userId;
        List<NotificationDTO> existingNotifications = getFromRedis(userId);

        if (existingNotifications != null && !existingNotifications.isEmpty()) {
            // 수정 가능한 새 리스트로 복사
            List<NotificationDTO> notifications = new java.util.ArrayList<>(existingNotifications);
            notifications.removeIf(n -> n.getIdx().equals(notificationId));
            notificationRedisTemplate.opsForValue().set(redisKey, notifications,
                    java.time.Duration.ofHours(REDIS_TTL_HOURS));
        }
    }

    /**
     * Redis와 DB 알림 병합 (중복 제거)
     */
    private List<NotificationDTO> mergeNotifications(List<NotificationDTO> redisNotifications,
            List<NotificationDTO> dbNotifications) {
        java.util.Set<Long> redisIds = redisNotifications.stream()
                .map(NotificationDTO::getIdx)
                .collect(java.util.stream.Collectors.toSet());

        // Redis에 없는 DB 알림만 추가
        List<NotificationDTO> merged = new java.util.ArrayList<>(redisNotifications);
        dbNotifications.stream()
                .filter(n -> !redisIds.contains(n.getIdx()))
                .forEach(merged::add);

        // 최신순 정렬
        merged.sort((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null)
                return 0;
            if (a.getCreatedAt() == null)
                return 1;
            if (b.getCreatedAt() == null)
                return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return merged;
    }
}
