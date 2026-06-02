package com.linkup.Petory.domain.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.notification.entity.FcmToken;
import com.linkup.Petory.domain.user.entity.Users;

/** FCM 토큰 JPA 리포지토리. 사용자별·토큰 문자열별 조회·삭제를 제공한다. */
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findByUser(Users user);

    Optional<FcmToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByUser(Users user);
}
