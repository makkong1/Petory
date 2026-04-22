package com.linkup.Petory.domain.board.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;

import lombok.RequiredArgsConstructor;

/**
 * BoardPopularitySnapshotRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaBoardPopularitySnapshotAdapter implements BoardPopularitySnapshotRepository {

    private final SpringDataJpaBoardPopularitySnapshotRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public List<BoardPopularitySnapshot> saveAll(List<BoardPopularitySnapshot> snapshots) {
        return jpaRepository.saveAll(snapshots);
    }

    @Override
    public List<BoardPopularitySnapshot> findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate) {
        return jpaRepository.findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
                periodType, periodStartDate, periodEndDate);
    }

    @SuppressWarnings("null")
    @Override
    public List<BoardPopularitySnapshot> findAll(Specification<BoardPopularitySnapshot> spec, Sort sort) {
        return jpaRepository.findAll(spec, sort);
    }

    @Override
    public List<BoardPopularitySnapshot> findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc(
            PopularityPeriodType periodType) {
        return jpaRepository.findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc(periodType);
    }

    @Override
    public void deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate) {
        jpaRepository.deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate(
                periodType, periodStartDate, periodEndDate);
    }
}
