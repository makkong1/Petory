package com.linkup.Petory.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * PetCoinEscrow 도메인 Repository 인터페이스입니다.
 */
public interface PetCoinEscrowRepository {

    PetCoinEscrow save(PetCoinEscrow escrow);

    Optional<PetCoinEscrow> findById(Long id);

    /**
     * CareRequest로 에스크로 조회
     */
    Optional<PetCoinEscrow> findByCareRequest(CareRequest careRequest);

    /**
     * 사용자별 에스크로 조회 (요청자 또는 제공자)
     */
    List<PetCoinEscrow> findByRequesterOrProvider(Users user);

    /**
     * 상태별 에스크로 조회
     */
    List<PetCoinEscrow> findByStatus(EscrowStatus status);

    /**
     * 비관적 락을 사용한 에스크로 조회 (동시성 제어용)
     * 상태 변경 시 Race Condition 방지를 위해 사용
     */
    Optional<PetCoinEscrow> findByIdForUpdate(Long idx);
}
