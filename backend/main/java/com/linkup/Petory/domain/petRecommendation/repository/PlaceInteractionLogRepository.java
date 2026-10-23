package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 장소 상호작용 로그 조회 전용 Repository.
 *
 * <p>
 * 추천 점수 계산에서 최근 기간별 location 집계를 가져올 때 사용한다.
 */
public interface PlaceInteractionLogRepository extends JpaRepository<PlaceInteractionLog, Long> {

    /**
     * 지정한 장소 목록의 최근 상호작용을 위치별로 집계한다.
     *
     * <p>
     * 조건: location IN + createdAt 기간 필터, 결과: location별 <b>서로 다른 사용자 수</b>.
     *
     * <p>
     * 🔴 행을 그대로 세면 안 된다 — 한 사용자가 같은 장소를 반복 조회하면 그만큼 점수가 오르고,
     * {@code PlaceInteractionService} 의 정규화가 1000 에서 포화되므로 <b>혼자서 특정 장소를 1위로
     * 밀 수 있다.</b> 원본 로그는 이후 집계·모델 개선에 재사용하려고 raw 로 남기므로
     * (see {@code PlaceInteractionService#record}), 중복 제거는 여기 읽기 시점에서 한다.
     */
    @Query("""
        SELECT new com.linkup.Petory.domain.petRecommendation.repository.LocationInteractionCount(
            p.locationIdx, COUNT(DISTINCT p.userIdx))
        FROM PlaceInteractionLog p
        WHERE p.locationIdx IN :locationIds
          AND p.createdAt >= :since
        GROUP BY p.locationIdx
        """)
    List<LocationInteractionCount> countByLocationIdsSince(
            @Param("locationIds") List<Long> locationIds,
            @Param("since") LocalDateTime since);
}
