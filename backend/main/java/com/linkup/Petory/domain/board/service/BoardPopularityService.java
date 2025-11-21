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
        
        System.out.println("=== 인기 게시글 조회 시작 ===");
        System.out.println("PeriodType: " + periodType);
        System.out.println("조회 기간: " + range.periodStart() + " ~ " + range.periodEnd());
        
        // 1. 정확한 날짜 매칭으로 조회 시도
        List<BoardPopularitySnapshot> snapshots = snapshotRepository
                .findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
                        periodType,
                        range.periodStart(),
                        range.periodEnd());
        System.out.println("1. 정확한 날짜 매칭 결과: " + snapshots.size() + "개");

        // 2. 정확한 매칭이 없으면 기간이 겹치는 스냅샷 조회 시도
        // 기간이 겹치는 조건: 스냅샷 시작일 <= 조회 종료일 AND 스냅샷 종료일 >= 조회 시작일
        if (snapshots.isEmpty()) {
            snapshots = snapshotRepository
                    .findByPeriodTypeAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByRankingAsc(
                            periodType,
                            range.periodEnd(),    // periodStartDate <= 이 값 (조회 종료일)
                            range.periodStart()); // periodEndDate >= 이 값 (조회 시작일)
            System.out.println("2. 기간 겹치는 스냅샷 조회 결과: " + snapshots.size() + "개");
            System.out.println("   조회 조건: periodStartDate <= " + range.periodEnd() + " AND periodEndDate >= " + range.periodStart());
            if (!snapshots.isEmpty()) {
                System.out.println("   첫 번째 스냅샷 기간: " + snapshots.get(0).getPeriodStartDate() + " ~ " + snapshots.get(0).getPeriodEndDate());
            }
        }

        // 3. 그래도 없으면 가장 최근 스냅샷 조회 시도
        if (snapshots.isEmpty()) {
            snapshots = snapshotRepository
                    .findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc(periodType);
            System.out.println("3. 가장 최근 스냅샷 조회 결과: " + snapshots.size() + "개");
            if (!snapshots.isEmpty()) {
                System.out.println("   가장 최근 스냅샷 기간: " + snapshots.get(0).getPeriodStartDate() + " ~ " + snapshots.get(0).getPeriodEndDate());
            }
        }

        // 4. 모든 시도가 실패하면 새로 생성
        if (snapshots.isEmpty()) {
            System.out.println("4. 새 스냅샷 생성 시작");
            snapshots = generateSnapshots(periodType, range);
            System.out.println("   생성된 스냅샷 수: " + snapshots.size() + "개");
        }

        System.out.println("=== 최종 반환 스냅샷 수: " + snapshots.size() + "개 ===");
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

