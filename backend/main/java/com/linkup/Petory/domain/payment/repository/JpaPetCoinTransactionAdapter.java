package com.linkup.Petory.domain.payment.repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * PetCoinTransactionRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaPetCoinTransactionAdapter implements PetCoinTransactionRepository {

    private final SpringDataJpaPetCoinTransactionRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public PetCoinTransaction save(PetCoinTransaction transaction) {
        return jpaRepository.save(transaction);
    }

    @Override
    public Optional<PetCoinTransaction> findByIdWithUser(Long idx) {
        return jpaRepository.findByIdWithUser(idx);
    }

    @Override
    public Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable) {
        return jpaRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }
}
