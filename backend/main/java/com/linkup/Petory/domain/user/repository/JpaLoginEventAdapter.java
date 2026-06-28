package com.linkup.Petory.domain.user.repository;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.LoginEvent;

import lombok.RequiredArgsConstructor;

@Repository
@Primary
@RequiredArgsConstructor
public class JpaLoginEventAdapter implements LoginEventRepository {

    private final SpringDataJpaLoginEventRepository jpaRepository;

    @Override
    public LoginEvent save(LoginEvent loginEvent) {
        return jpaRepository.save(loginEvent);
    }

    @Override
    public long countDistinctUsersBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countDistinctUsersBetween(start, end);
    }
}
