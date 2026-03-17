package com.linkup.Petory.domain.board.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;

/**
 * BoardPopularitySnapshot 도메인 Repository 인터페이스입니다.
 */
public interface BoardPopularitySnapshotRepository {

    BoardPopularitySnapshot save(BoardPopularitySnapshot snapshot);

    List<BoardPopularitySnapshot> saveAll(List<BoardPopularitySnapshot> snapshots);

    Optional<BoardPopularitySnapshot> findById(Long id);

    void delete(BoardPopularitySnapshot snapshot);

    void deleteById(Long id);

    List<BoardPopularitySnapshot> findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate);

    /**
     * Specification 기반 조회 (기간 겹침 등 동적 조건)
     */
    List<BoardPopularitySnapshot> findAll(Specification<BoardPopularitySnapshot> spec, Sort sort);

    /**
     * 가장 최근 스냅샷 조회
     */
    List<BoardPopularitySnapshot> findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc(
            PopularityPeriodType periodType);

    void deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate);
}
