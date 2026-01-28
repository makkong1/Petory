package com.linkup.Petory.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * PetCoinEscrowRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaPetCoinEscrowAdapter implements PetCoinEscrowRepository {

    private final SpringDataJpaPetCoinEscrowRepository jpaRepository;

    @Override
    public PetCoinEscrow save(PetCoinEscrow escrow) {
        return jpaRepository.save(escrow);
    }

    @Override
    public Optional<PetCoinEscrow> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<PetCoinEscrow> findByCareRequest(CareRequest careRequest) {
        return jpaRepository.findByCareRequest(careRequest);
    }

    @Override
    public List<PetCoinEscrow> findByRequesterOrProvider(Users user) {
        return jpaRepository.findByRequesterOrProvider(user);
    }

    @Override
    public List<PetCoinEscrow> findByStatus(EscrowStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public Optional<PetCoinEscrow> findByIdForUpdate(Long idx) {
        return jpaRepository.findByIdForUpdate(idx);
    }
}
