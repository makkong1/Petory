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
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaPetCoinTransactionRepository
        extends JpaRepository<PetCoinTransaction, Long> {

    @RepositoryMethod("펫코인 거래: 사용자별 목록 조회")
    List<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user);

    @RepositoryMethod("펫코인 거래: 사용자+거래 유형별 목록 조회")
    List<PetCoinTransaction> findByUserAndTransactionTypeOrderByCreatedAtDesc(
            Users user, TransactionType transactionType);

    @RepositoryMethod("펫코인 거래: 사용자+상태별 목록 조회")
    List<PetCoinTransaction> findByUserAndStatusOrderByCreatedAtDesc(
            Users user, TransactionStatus status);

    @Query("SELECT t FROM PetCoinTransaction t WHERE t.relatedType = :relatedType AND t.relatedIdx = :relatedIdx ORDER BY t.createdAt DESC")
    List<PetCoinTransaction> findByRelatedTypeAndRelatedIdx(
            @Param("relatedType") String relatedType,
            @Param("relatedIdx") Long relatedIdx);

    @RepositoryMethod("펫코인 거래: 사용자별 페이징 조회")
    @EntityGraph(attributePaths = "user")
    Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);
}
