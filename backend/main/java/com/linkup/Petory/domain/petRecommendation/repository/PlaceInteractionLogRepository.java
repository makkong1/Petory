package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PlaceInteractionLogRepository extends JpaRepository<PlaceInteractionLog, Long> {

    @Query("""
        SELECT p.locationIdx, COUNT(p) AS cnt
        FROM PlaceInteractionLog p
        WHERE p.locationIdx IN :locationIds
          AND p.createdAt >= :since
        GROUP BY p.locationIdx
        """)
    List<Object[]> countByLocationIdsSince(
            @Param("locationIds") List<Long> locationIds,
            @Param("since") LocalDateTime since);
}
