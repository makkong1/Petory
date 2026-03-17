package com.linkup.Petory.domain.board.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;

/**
 * BoardPopularitySnapshot 조회용 Specification 팩토리입니다.
 */
public final class BoardPopularitySnapshotSpecs {

    private BoardPopularitySnapshotSpecs() {
    }

    /**
     * 기간이 겹치는 스냅샷 조회 조건
     * 조건: periodType 일치 AND 스냅샷 시작일 <= 조회 종료일 AND 스냅샷 종료일 >= 조회 시작일
     */
    public static Specification<BoardPopularitySnapshot> periodOverlaps(
            PopularityPeriodType periodType,
            LocalDate periodStart,
            LocalDate periodEnd) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("periodType"), periodType),
                cb.lessThanOrEqualTo(root.get("periodStartDate"), periodEnd),
                cb.greaterThanOrEqualTo(root.get("periodEndDate"), periodStart));
    }
}
