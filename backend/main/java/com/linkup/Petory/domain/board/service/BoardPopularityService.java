package com.linkup.Petory.domain.board.service;

import com.linkup.Petory.domain.board.converter.BoardPopularitySnapshotConverter;
import com.linkup.Petory.domain.board.dto.BoardPopularitySnapshotDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import com.linkup.Petory.domain.board.repository.BoardPopularitySnapshotRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardPopularityService {

    private static final String TARGET_CATEGORY = "PRIDE";

    private final BoardRepository boardRepository;
    private final BoardPopularitySnapshotRepository snapshotRepository;
    private final BoardPopularitySnapshotConverter snapshotConverter;

    @Transactional
    public List<BoardPopularitySnapshotDTO> getPopularBoards(PopularityPeriodType periodType) {
        PeriodRange range = calculateRange(periodType);
        List<BoardPopularitySnapshot> snapshots = snapshotRepository
                .findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
                        periodType,
                        range.periodStart(),
                        range.periodEnd());

        if (snapshots.isEmpty()) {
            snapshots = generateSnapshots(periodType, range);
        }

        return snapshotConverter.toDTOList(snapshots);
    }

    @Transactional
    public List<BoardPopularitySnapshot> generateSnapshots(PopularityPeriodType periodType) {
        PeriodRange range = calculateRange(periodType);
        return generateSnapshots(periodType, range);
    }

    private List<BoardPopularitySnapshot> generateSnapshots(PopularityPeriodType periodType, PeriodRange range) {
        LocalDateTime startDateTime = range.periodStart().atStartOfDay();
        LocalDateTime endDateTime = range.periodEnd().plusDays(1).atStartOfDay();

        List<Board> prideBoards = boardRepository.findByCategoryAndCreatedAtBetween(
                TARGET_CATEGORY,
                startDateTime,
                endDateTime);

        List<BoardScore> rankedBoards = prideBoards.stream()
                .map(board -> new BoardScore(board, calculatePopularityScore(board)))
                .sorted(Comparator.comparingInt(BoardScore::score).reversed()
                        .thenComparing(bs -> bs.board().getCreatedAt(), Comparator.reverseOrder()))
                .limit(30)
                .collect(Collectors.toList());

        snapshotRepository.deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate(
                periodType,
                range.periodStart(),
                range.periodEnd());

        List<BoardPopularitySnapshot> snapshots = createSnapshots(periodType, range, rankedBoards);
        return snapshotRepository.saveAll(snapshots);
    }

    private List<BoardPopularitySnapshot> createSnapshots(
            PopularityPeriodType periodType,
            PeriodRange range,
            List<BoardScore> rankedBoards) {

        final int[] rankCounter = { 1 };

        return rankedBoards.stream()
                .map(score -> BoardPopularitySnapshot.builder()
                        .board(score.board())
                        .periodType(periodType)
                        .periodStartDate(range.periodStart())
                        .periodEndDate(range.periodEnd())
                        .ranking(rankCounter[0]++)
                        .popularityScore(score.score())
                        .likeCount(defaultValue(score.board().getLikeCount()))
                        .commentCount(defaultValue(score.board().getCommentCount()))
                        .viewCount(defaultValue(score.board().getViewCount()))
                        .build())
                .collect(Collectors.toList());
    }

    private int calculatePopularityScore(Board board) {
        int likes = defaultValue(board.getLikeCount());
        int comments = defaultValue(board.getCommentCount());
        int views = defaultValue(board.getViewCount());
        return (likes * 3) + (comments * 2) + views;
    }

    private int defaultValue(Integer value) {
        return value == null ? 0 : value;
    }

    private PeriodRange calculateRange(PopularityPeriodType periodType) {
        LocalDate today = LocalDate.now();
        LocalDate periodEnd = today;
        LocalDate periodStart = switch (periodType) {
            case WEEKLY -> today.minusDays(6); // include today => 7 days
            case MONTHLY -> today.minusDays(29); // include today => 30 days
        };
        return new PeriodRange(periodStart, periodEnd);
    }

    private record PeriodRange(LocalDate periodStart, LocalDate periodEnd) {
    }

    private record BoardScore(Board board, int score) {
    }
}

