package com.linkup.Petory.domain.user.repository;

import java.util.Optional;

import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.entity.SocialUser;

/**
 * SocialUser 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaSocialUserAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface SocialUserRepository {

    SocialUser save(SocialUser socialUser);

    Optional<SocialUser> findById(Long id);

    void delete(SocialUser socialUser);

    void deleteById(Long id);

    /**
     * Provider와 ProviderId로 소셜 사용자 조회
     */
    Optional<SocialUser> findByProviderAndProviderId(Provider provider, String providerId);
}
