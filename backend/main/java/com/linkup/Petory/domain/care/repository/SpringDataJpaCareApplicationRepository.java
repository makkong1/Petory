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
     *
     * <p><b>완료된 요청도 포함한다.</b> 처음엔 "끝난 계약은 띄울 것이 없다"고 보고 OPEN·IN_PROGRESS
     * 만 넣었는데, 완료 배너와 리뷰 작성 버튼이 COMPLETED 에서만 뜨므로 그 둘이 영영 못 뜨게 됐다.
     * 화면 조건과 데이터 소스가 어긋나 UI 가 조용히 사라지는 것 — 이 리팩터링이 없앤 버그와 같은
     * 모양이라, 같은 실수를 두 번 한 자리다.
     *
     * 취소된 요청과 끝난 제안(거절·철회)은 제외한다. 그쪽은 정말 띄울 것이 없다.
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
                                com.linkup.Petory.domain.care.entity.CareRequestStatus.IN_PROGRESS,
                                com.linkup.Petory.domain.care.entity.CareRequestStatus.COMPLETED)
              AND ((cr.user.idx = :userA AND p.idx = :userB)
                OR (cr.user.idx = :userB AND p.idx = :userA))
            ORDER BY ca.createdAt DESC
            """)
    List<CareApplication> findLiveOffersBetween(@Param("userA") Long userA, @Param("userB") Long userB);
}
