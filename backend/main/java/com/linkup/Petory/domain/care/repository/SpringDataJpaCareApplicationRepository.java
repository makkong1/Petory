package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCareApplicationRepository extends JpaRepository<CareApplication, Long> {

    @RepositoryMethod("펫케어 지원: 요청별 목록 조회")
    List<CareApplication> findByCareRequest(CareRequest careRequest);

    @RepositoryMethod("펫케어 지원: 요청+제공자로 조회")
    @Query("SELECT ca FROM CareApplication ca " +
                    "WHERE ca.careRequest.idx = :careRequestIdx " +
                    "  AND ca.provider.idx = :providerIdx")
    Optional<CareApplication> findByCareRequestIdxAndProviderIdx(
                    @Param("careRequestIdx") Long careRequestIdx,
                    @Param("providerIdx") Long providerIdx);

    @RepositoryMethod("펫케어 지원: 요청+상태로 조회")
    @Query("SELECT ca FROM CareApplication ca " +
                    "WHERE ca.careRequest.idx = :careRequestIdx " +
                    "  AND ca.status = :status")
    Optional<CareApplication> findByCareRequestIdxAndStatus(
                    @Param("careRequestIdx") Long careRequestIdx,
                    @Param("status") CareApplicationStatus status);
}

