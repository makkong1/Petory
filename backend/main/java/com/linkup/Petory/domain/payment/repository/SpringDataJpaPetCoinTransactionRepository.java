package com.linkup.Petory.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaPetCoinTransactionRepository
        extends JpaRepository<PetCoinTransaction, Long> {

    @RepositoryMethod("펫코인 거래: 단건 조회 (user 포함)")
    @Query("SELECT t FROM PetCoinTransaction t JOIN FETCH t.user WHERE t.idx = :idx")
    Optional<PetCoinTransaction> findByIdWithUser(@Param("idx") Long idx);

    // [오버페칭 제거] user는 파라미터로 이미 받으며, 컨버터는 getUser().getIdx()(FK, LAZY 프록시라 쿼리 없음)만 사용한다.
    // 기존 @EntityGraph는 매 조회마다 users 전체 컬럼을 불필요하게 로딩했으므로 제거한다.
    @RepositoryMethod("펫코인 거래: 사용자별 페이징 조회")
    Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);
}
