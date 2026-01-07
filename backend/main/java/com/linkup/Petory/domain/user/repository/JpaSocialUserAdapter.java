package com.linkup.Petory.domain.user.repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.entity.SocialUser;

import lombok.RequiredArgsConstructor;

/**
 * SocialUserRepository의 JPA 구현체(어댑터)입니다.
 * 
 * 이 클래스는 Spring Data JPA를 사용하여 SocialUserRepository 인터페이스를 구현합니다.
 * 나중에 다른 DB나 DBMS로 변경할 경우, 이 어댑터와 유사한 새 클래스를 만들고
 * 
 * @Primary 어노테이션을 옮기면 됩니다.
 * 
 *          예시:
 *          - MyBatis로 변경: MyBatisSocialUserAdapter 생성 후 @Primary 이동
 *          - MongoDB로 변경: MongoSocialUserAdapter 생성 후 @Primary 이동
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaSocialUserAdapter implements SocialUserRepository {

    private final SpringDataJpaSocialUserRepository jpaRepository;

    @Override
    public SocialUser save(SocialUser socialUser) {
        return jpaRepository.save(socialUser);
    }

    @Override
    public Optional<SocialUser> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(SocialUser socialUser) {
        jpaRepository.delete(socialUser);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public Optional<SocialUser> findByProviderAndProviderId(Provider provider, String providerId) {
        return jpaRepository.findByProviderAndProviderId(provider, providerId);
    }
}

