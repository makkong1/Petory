package com.linkup.Petory.domain.board.repository;

import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BoardPopularitySnapshotRepository extends JpaRepository<BoardPopularitySnapshot, Long> {

        List<BoardPopularitySnapshot> findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
                        PopularityPeriodType periodType,
                        LocalDate periodStartDate,
                        LocalDate periodEndDate);

        // 기간 범위 내의 스냅샷 조회 (기간이 겹치는 경우)
        // 조건: 스냅샷 시작일 <= 조회 종료일 AND 스냅샷 종료일 >= 조회 시작일
        List<BoardPopularitySnapshot> findByPeriodTypeAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByRankingAsc(
                        PopularityPeriodType periodType,
                        LocalDate periodStartDate, // 스냅샷 시작일 <= 이 값 (조회 종료일)
                        LocalDate periodEndDate); // 스냅샷 종료일 >= 이 값 (조회 시작일)

        // 가장 최근 스냅샷 조회
        List<BoardPopularitySnapshot> findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc(
                        PopularityPeriodType periodType);

        void deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate(
                        PopularityPeriodType periodType,
                        LocalDate periodStartDate,
                        LocalDate periodEndDate);
}
