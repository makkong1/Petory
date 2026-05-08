package com.linkup.Petory.domain.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.notification.entity.FcmToken;
import com.linkup.Petory.domain.user.entity.Users;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findByUser(Users user);

    Optional<FcmToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByUser(Users user);
}
