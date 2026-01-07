package com.linkup.Petory.domain.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.notification.entity.Notification;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * NotificationRepository의 JPA 구현체입니다.
 * 나중에 MyBatis나 NoSQL로 바꿀 경우, 이 어댑터와 유사한 새 클래스를 만들고 @Primary를 옮기면 됩니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaNotificationAdapter implements NotificationRepository {

    private final SpringDataJpaNotificationRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public List<Notification> saveAll(List<Notification> notifications) {
        return jpaRepository.saveAll(notifications);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Notification> findByUserOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Override
    public List<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user);
    }

    @Override
    public Long countUnreadByUser(Users user) {
        return jpaRepository.countUnreadByUser(user);
    }
}
