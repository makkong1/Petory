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
     * <p>기준은 <b>"아직 내가 할 게 남은 계약"</b>이다. 화면이 카드 하나를 그리는 조건과 같다.
     * <ul>
     *   <li>대기 중 제안 — 수락/거절(제공자) 또는 응답 대기(요청자)</li>
     *   <li>진행 중 — 이행 완료 확인</li>
     *   <li>완료 + 내가 요청자 + 아직 리뷰를 안 씀 — 리뷰 작성</li>
     * </ul>
     *
     * <p>완료된 요청을 처음엔 통째로 뺐다가("끝난 계약은 띄울 것이 없다"), 완료 배너와 리뷰
     * 버튼이 COMPLETED 에서만 뜨는 바람에 그 둘이 영영 못 뜨게 됐다. 그래서 넣었더니 이번엔
     * <b>끝나는 조건이 없어 완료 카드가 영구히 쌓였다</b> — 한 상대와 열 번 거래하면 완료 배너가
     * 열 장 깔린다. 리뷰를 써도 안 사라졌다.
     *
     * <p>그래서 완료 건은 <b>리뷰가 남았을 때만</b> 남긴다. 리뷰를 쓰면 카드가 사라지고,
     * 제공자에게는 쓸 리뷰가 없으니 완료 즉시 내려간다. 덕분에 화면이 "리뷰 썼는지"를 따로
     * 물어볼 필요도 없어진다 — 카드가 있으면 쓸 게 있는 것이다.
     *
     * <p>취소된 요청과 끝난 제안(거절·철회)은 제외한다. 그쪽은 정말 띄울 것이 없다.
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
              AND (cr.status <> com.linkup.Petory.domain.care.entity.CareRequestStatus.COMPLETED
                   OR (cr.user.idx = :userA
                       AND NOT EXISTS (SELECT 1 FROM CareReview rv
                                       WHERE rv.careApplication.idx = ca.idx)))
            ORDER BY ca.createdAt DESC
            """)
    List<CareApplication> findLiveOffersBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    /**
     * 여러 제공자의 완료 케어 건수를 한 번에. countCompletedByProviderId 의 배치 판이다.
     * 반환 각 행: [providerIdx(Long), completedCount(Long)]
     */
    @Query("""
            SELECT ca.provider.idx, COUNT(ca)
            FROM CareApplication ca
            JOIN ca.careRequest cr
            WHERE ca.provider.idx IN :providerIdxs
              AND ca.status = com.linkup.Petory.domain.care.entity.CareApplicationStatus.ACCEPTED
              AND cr.status = com.linkup.Petory.domain.care.entity.CareRequestStatus.COMPLETED
              AND cr.isDeleted = false
            GROUP BY ca.provider.idx
            """)
    List<Object[]> countCompletedByProviderIdxs(@Param("providerIdxs") List<Long> providerIdxs);
}
