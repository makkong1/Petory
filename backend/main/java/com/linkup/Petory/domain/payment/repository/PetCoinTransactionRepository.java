package com.linkup.Petory.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * PetCoinTransaction 도메인 Repository 인터페이스입니다.
 *
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 */
public interface PetCoinTransactionRepository {

    PetCoinTransaction save(PetCoinTransaction transaction);

    /**
     * 단건 조회 (user 포함) - 상세 조회 시 권한 확인용
     */
    Optional<PetCoinTransaction> findByIdWithUser(Long idx);

    /**
     * 사용자별 거래 내역 페이징 조회 (최신순)
     */
    Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);
}
