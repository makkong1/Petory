package com.linkup.Petory.domain.payment.repository;

import java.util.Optional;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;

/**
 * PetCoinEscrow 도메인 Repository 인터페이스입니다.
 */
public interface PetCoinEscrowRepository {

    PetCoinEscrow save(PetCoinEscrow escrow);

    /**
     * CareRequest로 에스크로 조회
     */
    Optional<PetCoinEscrow> findByCareRequest(CareRequest careRequest);

    /**
     * 케어 요청 ID로 에스크로 조회 (requester, provider, careRequest 포함)
     */
    Optional<PetCoinEscrow> findByCareRequestIdxWithDetails(Long careRequestIdx);

    /**
     * 비관적 락을 사용한 CareRequest로 에스크로 조회 (동시성 제어용) 상태 변경 시 Race Condition 방지를 위해
     * 사용
     */
    Optional<PetCoinEscrow> findByCareRequestForUpdate(CareRequest careRequest);

    /**
     * 비관적 락을 사용한 에스크로 조회 (동시성 제어용) 상태 변경 시 Race Condition 방지를 위해 사용
     */
    Optional<PetCoinEscrow> findByIdForUpdate(Long idx);
}
