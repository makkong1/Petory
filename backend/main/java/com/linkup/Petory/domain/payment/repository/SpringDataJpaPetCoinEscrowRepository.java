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

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaPetCoinEscrowRepository
        extends JpaRepository<PetCoinEscrow, Long> {

    Optional<PetCoinEscrow> findByCareRequest(CareRequest careRequest);

    @Query("SELECT e FROM PetCoinEscrow e WHERE e.careRequest.idx = :careRequestIdx")
    Optional<PetCoinEscrow> findByCareRequestIdx(@Param("careRequestIdx") Long careRequestIdx);

    /**
     * 비관적 락을 사용한 CareRequest로 에스크로 조회 (동시성 제어용)
     * 상태 변경 시 Race Condition 방지를 위해 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PetCoinEscrow e WHERE e.careRequest = :careRequest")
    Optional<PetCoinEscrow> findByCareRequestForUpdate(@Param("careRequest") CareRequest careRequest);

    @Query("SELECT e FROM PetCoinEscrow e WHERE e.requester = :user OR e.provider = :user ORDER BY e.createdAt DESC")
    List<PetCoinEscrow> findByRequesterOrProvider(@Param("user") Users user);

    List<PetCoinEscrow> findByStatus(EscrowStatus status);

    /**
     * 비관적 락을 사용한 에스크로 조회 (동시성 제어용)
     * 상태 변경 시 Race Condition 방지를 위해 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PetCoinEscrow e WHERE e.idx = :idx")
    Optional<PetCoinEscrow> findByIdForUpdate(@Param("idx") Long idx);
}
