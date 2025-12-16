package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;

@Repository
public interface CareApplicationRepository extends JpaRepository<CareApplication, Long> {

        /**
         * 특정 CareRequest에 대한 지원 목록 조회
         */
        List<CareApplication> findByCareRequest(CareRequest careRequest);

        /**
         * 특정 CareRequest와 Provider로 지원 조회
         */
        @Query("SELECT ca FROM CareApplication ca " +
                        "WHERE ca.careRequest.idx = :careRequestIdx " +
                        "  AND ca.provider.idx = :providerIdx")
        Optional<CareApplication> findByCareRequestIdxAndProviderIdx(
                        @Param("careRequestIdx") Long careRequestIdx,
                        @Param("providerIdx") Long providerIdx);

        /**
         * 특정 CareRequest의 ACCEPTED 상태 지원 조회
         */
        @Query("SELECT ca FROM CareApplication ca " +
                        "WHERE ca.careRequest.idx = :careRequestIdx " +
                        "  AND ca.status = :status")
        Optional<CareApplication> findByCareRequestIdxAndStatus(
                        @Param("careRequestIdx") Long careRequestIdx,
                        @Param("status") CareApplicationStatus status);
}
