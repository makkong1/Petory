package com.linkup.Petory.domain.notification.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.notification.entity.Notification;

/**
 * Notification 도메인 Repository 인터페이스입니다.
 *
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다. 다양한 데이터베이스 구현체(JPA,
 * MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 *
 * 구현체: - JpaNotificationAdapter: JPA 기반 구현체 - 다른 DB로 변경 시 새로운 어댑터를 만들고
 * @Primary를 옮기면 됩니다.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    /**
     * 사용자별 알림 목록 조회 (최신순)
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 사용자별 읽지 않은 알림 목록 조회 (최신순)
     */
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    /**
     * 사용자별 읽지 않은 알림 개수 조회
     */
    Long countUnreadByUserId(Long userId);

    /**
     * 사용자의 모든 읽지 않은 알림을 단일 bulk UPDATE로 읽음 처리
     *
     * @return 변경된 알림 row 수
     */
    int markAllAsReadByUserId(Long userId);
}
