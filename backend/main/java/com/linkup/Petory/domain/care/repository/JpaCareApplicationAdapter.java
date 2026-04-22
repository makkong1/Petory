package com.linkup.Petory.domain.care.repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareApplication;

import lombok.RequiredArgsConstructor;

/**
 * CareApplicationRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaCareApplicationAdapter implements CareApplicationRepository {

    private final SpringDataJpaCareApplicationRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public CareApplication saveAndFlush(CareApplication careApplication) {
        return jpaRepository.saveAndFlush(careApplication);
    }

    @SuppressWarnings("null")
    @Override
    public Optional<CareApplication> findById(Long id) {
        return jpaRepository.findById(id);
    }
}
