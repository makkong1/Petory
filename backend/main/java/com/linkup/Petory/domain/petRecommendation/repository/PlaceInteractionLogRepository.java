package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PlaceInteractionLogRepository extends JpaRepository<PlaceInteractionLog, Long> {

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
