package com.linkup.Petory.domain.payment.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.entity.TransactionStatus;
import com.linkup.Petory.domain.payment.entity.TransactionType;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaPetCoinTransactionRepository
        extends JpaRepository<PetCoinTransaction, Long> {

    List<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user);

    List<PetCoinTransaction> findByUserAndTransactionTypeOrderByCreatedAtDesc(
            Users user, TransactionType transactionType);

    List<PetCoinTransaction> findByUserAndStatusOrderByCreatedAtDesc(
            Users user, TransactionStatus status);

    @Query("SELECT t FROM PetCoinTransaction t WHERE t.relatedType = :relatedType AND t.relatedIdx = :relatedIdx ORDER BY t.createdAt DESC")
    List<PetCoinTransaction> findByRelatedTypeAndRelatedIdx(
            @Param("relatedType") String relatedType,
            @Param("relatedIdx") Long relatedIdx);

    /**
     * 사용자별 거래 내역 페이징 조회 (JOIN FETCH user로 N+1 방지)
     */
    @EntityGraph(attributePaths = "user")
    Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);
}
