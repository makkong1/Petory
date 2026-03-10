package com.linkup.Petory.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaPetCoinEscrowRepository
        extends JpaRepository<PetCoinEscrow, Long> {

    @RepositoryMethod("에스크로: 케어 요청으로 조회")
    Optional<PetCoinEscrow> findByCareRequest(CareRequest careRequest);

    @RepositoryMethod("에스크로: 케어 요청 ID로 조회")
    @Query("SELECT e FROM PetCoinEscrow e WHERE e.careRequest.idx = :careRequestIdx")
    Optional<PetCoinEscrow> findByCareRequestIdx(@Param("careRequestIdx") Long careRequestIdx);

    @RepositoryMethod("에스크로: 케어 요청 ID로 조회 (requester, provider, careRequest 포함)")
    @Query("SELECT e FROM PetCoinEscrow e JOIN FETCH e.requester JOIN FETCH e.provider JOIN FETCH e.careRequest WHERE e.careRequest.idx = :careRequestIdx")
    Optional<PetCoinEscrow> findByCareRequestIdxWithDetails(@Param("careRequestIdx") Long careRequestIdx);

    /**
     * 비관적 락을 사용한 CareRequest로 에스크로 조회 (동시성 제어용)
     * 상태 변경 시 Race Condition 방지를 위해 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PetCoinEscrow e WHERE e.careRequest = :careRequest")
    Optional<PetCoinEscrow> findByCareRequestForUpdate(@Param("careRequest") CareRequest careRequest);

    @RepositoryMethod("에스크로: 요청자/제공자별 목록 조회")
    @Query("SELECT e FROM PetCoinEscrow e WHERE e.requester = :user OR e.provider = :user ORDER BY e.createdAt DESC")
    List<PetCoinEscrow> findByRequesterOrProvider(@Param("user") Users user);

    @RepositoryMethod("에스크로: 상태별 목록 조회")
    List<PetCoinEscrow> findByStatus(EscrowStatus status);

    @RepositoryMethod("에스크로: ID 비관적 락 조회")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PetCoinEscrow e WHERE e.idx = :idx")
    Optional<PetCoinEscrow> findByIdForUpdate(@Param("idx") Long idx);
}
