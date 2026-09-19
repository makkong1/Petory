package com.linkup.Petory.domain.care.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    /**
     * 두 사용자 사이에 살아 있는 케어 제안. 한쪽이 요청자이고 다른 쪽이 제공자인 경우를 양방향으로
     * 찾는다 — 채팅방은 더 이상 계약을 모르므로, 방에서 계약 UI 를 띄우려면 이 조회가 필요하다.
     * 끝난 계약(거절·완료·취소)은 띄울 것이 없어 제외한다.
     */
    @Query("""
            SELECT ca
            FROM CareApplication ca
            JOIN FETCH ca.careRequest cr
            JOIN FETCH ca.provider p
            WHERE cr.isDeleted = false
              AND ca.status IN (com.linkup.Petory.domain.care.entity.CareApplicationStatus.PENDING,
                                com.linkup.Petory.domain.care.entity.CareApplicationStatus.ACCEPTED)
              AND cr.status IN (com.linkup.Petory.domain.care.entity.CareRequestStatus.OPEN,
                                com.linkup.Petory.domain.care.entity.CareRequestStatus.IN_PROGRESS)
              AND ((cr.user.idx = :userA AND p.idx = :userB)
                OR (cr.user.idx = :userB AND p.idx = :userA))
            ORDER BY ca.createdAt DESC
            """)
    List<CareApplication> findLiveOffersBetween(@Param("userA") Long userA, @Param("userB") Long userB);
}
