package com.linkup.Petory.domain.care.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.care.entity.CareApplication;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCareApplicationRepository extends JpaRepository<CareApplication, Long> {

    @Query("""
            SELECT COUNT(ca)
            FROM CareApplication ca
            JOIN ca.careRequest cr
            WHERE ca.provider.idx = :providerId
              AND ca.status = com.linkup.Petory.domain.care.entity.CareApplicationStatus.ACCEPTED
              AND cr.status = com.linkup.Petory.domain.care.entity.CareRequestStatus.COMPLETED
              AND cr.isDeleted = false
            """)
    long countCompletedByProviderId(@Param("providerId") Long providerId);
}
