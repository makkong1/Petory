package com.linkup.Petory.domain.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.notification.entity.Notification;
import com.linkup.Petory.domain.user.entity.Users;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserOrderByCreatedAtDesc(Users user);

    List<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(Users user);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.isRead = false")
    Long countUnreadByUser(@Param("user") Users user);
}

