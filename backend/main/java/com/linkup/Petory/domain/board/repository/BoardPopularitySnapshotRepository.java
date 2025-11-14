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

    void deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate);
}

