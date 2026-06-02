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
 * <p>추천 점수 계산에서 최근 기간별 location 집계를 가져올 때 사용한다.
 */
public interface PlaceInteractionLogRepository extends JpaRepository<PlaceInteractionLog, Long> {

    /**
     * 지정한 장소 목록의 최근 상호작용 건수를 위치별로 집계한다.
     *
     * <p>조건: location IN + createdAt 기간 필터, 결과: location별 COUNT
     */
    @Query("""
        SELECT new com.linkup.Petory.domain.petRecommendation.repository.LocationInteractionCount(
            p.locationIdx, COUNT(p))
        FROM PlaceInteractionLog p
        WHERE p.locationIdx IN :locationIds
          AND p.createdAt >= :since
        GROUP BY p.locationIdx
        """)
    List<LocationInteractionCount> countByLocationIdsSince(
            @Param("locationIds") List<Long> locationIds,
            @Param("since") LocalDateTime since);
}
