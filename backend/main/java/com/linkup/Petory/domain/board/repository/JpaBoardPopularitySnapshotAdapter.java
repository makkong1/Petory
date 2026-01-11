package com.linkup.Petory.domain.board.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
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

    @Override
    public BoardPopularitySnapshot save(BoardPopularitySnapshot snapshot) {
        return jpaRepository.save(snapshot);
    }

    @Override
    public List<BoardPopularitySnapshot> saveAll(List<BoardPopularitySnapshot> snapshots) {
        return jpaRepository.saveAll(snapshots);
    }

    @Override
    public Optional<BoardPopularitySnapshot> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(BoardPopularitySnapshot snapshot) {
        jpaRepository.delete(snapshot);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<BoardPopularitySnapshot> findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate) {
        return jpaRepository.findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
                periodType, periodStartDate, periodEndDate);
    }

    @Override
    public List<BoardPopularitySnapshot> findByPeriodTypeAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByRankingAsc(
            PopularityPeriodType periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate) {
        return jpaRepository.findByPeriodTypeAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByRankingAsc(
                periodType, periodStartDate, periodEndDate);
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

