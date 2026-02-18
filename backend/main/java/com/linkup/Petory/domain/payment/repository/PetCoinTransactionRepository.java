package com.linkup.Petory.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.entity.TransactionStatus;
import com.linkup.Petory.domain.payment.entity.TransactionType;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * PetCoinTransaction 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 */
public interface PetCoinTransactionRepository {

    PetCoinTransaction save(PetCoinTransaction transaction);

    Optional<PetCoinTransaction> findById(Long id);

    /**
     * 사용자별 거래 내역 조회 (최신순)
     */
    List<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user);

    /**
     * 사용자별 거래 내역 페이징 조회 (최신순)
     */
    Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);

    /**
     * 사용자별 거래 타입별 내역 조회
     */
    List<PetCoinTransaction> findByUserAndTransactionTypeOrderByCreatedAtDesc(
            Users user, TransactionType transactionType);

    /**
     * 사용자별 거래 상태별 내역 조회
     */
    List<PetCoinTransaction> findByUserAndStatusOrderByCreatedAtDesc(
            Users user, TransactionStatus status);

    /**
     * 관련 엔티티로 거래 내역 조회
     */
    List<PetCoinTransaction> findByRelatedTypeAndRelatedIdx(
            String relatedType, Long relatedIdx);
}
