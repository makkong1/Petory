package com.linkup.Petory.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.entity.TransactionStatus;
import com.linkup.Petory.domain.payment.entity.TransactionType;
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

    @Override
    public PetCoinTransaction save(PetCoinTransaction transaction) {
        return jpaRepository.save(transaction);
    }

    @Override
    public Optional<PetCoinTransaction> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Override
    public Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable) {
        return jpaRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    @Override
    public List<PetCoinTransaction> findByUserAndTransactionTypeOrderByCreatedAtDesc(
            Users user, TransactionType transactionType) {
        return jpaRepository.findByUserAndTransactionTypeOrderByCreatedAtDesc(user, transactionType);
    }

    @Override
    public List<PetCoinTransaction> findByUserAndStatusOrderByCreatedAtDesc(
            Users user, TransactionStatus status) {
        return jpaRepository.findByUserAndStatusOrderByCreatedAtDesc(user, status);
    }

    @Override
    public List<PetCoinTransaction> findByRelatedTypeAndRelatedIdx(
            String relatedType, Long relatedIdx) {
        return jpaRepository.findByRelatedTypeAndRelatedIdx(relatedType, relatedIdx);
    }
}
