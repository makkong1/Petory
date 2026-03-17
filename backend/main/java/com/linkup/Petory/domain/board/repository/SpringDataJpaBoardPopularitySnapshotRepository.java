package com.linkup.Petory.domain.board.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaBoardPopularitySnapshotRepository extends JpaRepository<BoardPopularitySnapshot, Long>, JpaSpecificationExecutor<BoardPopularitySnapshot> {

    @RepositoryMethod("인기글 스냅샷: 기간별 조회")
    List<BoardPopularitySnapshot> findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate);

    @RepositoryMethod("인기글 스냅샷: 최신 30건 조회")
    List<BoardPopularitySnapshot> findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc(
            PopularityPeriodType periodType);

    @RepositoryMethod("인기글 스냅샷: 기간별 삭제")
    void deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate);
}

