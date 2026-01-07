package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;

import lombok.RequiredArgsConstructor;

/**
 * CareApplicationRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaCareApplicationAdapter implements CareApplicationRepository {

    private final SpringDataJpaCareApplicationRepository jpaRepository;

    @Override
    public CareApplication save(CareApplication careApplication) {
        return jpaRepository.save(careApplication);
    }

    @Override
    public CareApplication saveAndFlush(CareApplication careApplication) {
        return jpaRepository.saveAndFlush(careApplication);
    }

    @Override
    public Optional<CareApplication> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(CareApplication careApplication) {
        jpaRepository.delete(careApplication);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<CareApplication> findByCareRequest(CareRequest careRequest) {
        return jpaRepository.findByCareRequest(careRequest);
    }

    @Override
    public Optional<CareApplication> findByCareRequestIdxAndProviderIdx(
            Long careRequestIdx,
            Long providerIdx) {
        return jpaRepository.findByCareRequestIdxAndProviderIdx(careRequestIdx, providerIdx);
    }

    @Override
    public Optional<CareApplication> findByCareRequestIdxAndStatus(
            Long careRequestIdx,
            CareApplicationStatus status) {
        return jpaRepository.findByCareRequestIdxAndStatus(careRequestIdx, status);
    }
}

