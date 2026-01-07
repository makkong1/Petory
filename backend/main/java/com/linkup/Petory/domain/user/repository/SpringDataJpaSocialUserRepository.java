package com.linkup.Petory.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.entity.SocialUser;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaSocialUserAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaSocialUserRepository extends JpaRepository<SocialUser, Long> {

    /**
     * Provider와 ProviderId로 소셜 사용자 조회
     */
    Optional<SocialUser> findByProviderAndProviderId(Provider provider, String providerId);
}

