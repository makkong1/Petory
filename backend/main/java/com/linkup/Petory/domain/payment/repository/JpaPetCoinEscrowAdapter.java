package com.linkup.Petory.domain.payment.repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;

import lombok.RequiredArgsConstructor;

/**
 * PetCoinEscrowRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaPetCoinEscrowAdapter implements PetCoinEscrowRepository {

    private final SpringDataJpaPetCoinEscrowRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public PetCoinEscrow save(PetCoinEscrow escrow) {
        return jpaRepository.save(escrow);
    }

    @Override
    public Optional<PetCoinEscrow> findByCareRequest(CareRequest careRequest) {
        return jpaRepository.findByCareRequest(careRequest);
    }

    @Override
    public Optional<PetCoinEscrow> findByCareRequestIdxWithDetails(Long careRequestIdx) {
        return jpaRepository.findByCareRequestIdxWithDetails(careRequestIdx);
    }

    @Override
    public Optional<PetCoinEscrow> findByCareRequestForUpdate(CareRequest careRequest) {
        return jpaRepository.findByCareRequestForUpdate(careRequest);
    }

    @Override
    public Optional<PetCoinEscrow> findByIdForUpdate(Long idx) {
        return jpaRepository.findByIdForUpdate(idx);
    }
}
