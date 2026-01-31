package com.linkup.Petory.domain.board.service;

import com.linkup.Petory.domain.board.converter.BoardPopularitySnapshotConverter;
import com.linkup.Petory.domain.board.dto.BoardPopularitySnapshotDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.repository.BoardPopularitySnapshotRepository;
import com.linkup.Petory.domain.board.repository.BoardReactionRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.BoardViewLogRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BoardPopularityService {

    private static final String TARGET_CATEGORY = "자랑"; // "PRIDE"에서 "자랑"으로 변경

    private final BoardRepository boardRepository;
    private final BoardPopularitySnapshotRepository snapshotRepository;
    private final BoardPopularitySnapshotConverter snapshotConverter;
    private final BoardReactionRepository boardReactionRepository;
    private final CommentRepository commentRepository;
    private final BoardViewLogRepository boardViewLogRepository;

    @Transactional
    public List<BoardPopularitySnapshotDTO> getPopularBoards(PopularityPeriodType periodType) {
        PeriodRange range = calculateRange(periodType);

        // 1. 정확한 날짜 매칭으로 조회 시도
        List<BoardPopularitySnapshot> snapshots = snapshotRepository
                .findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc(
                        periodType,
                        range.periodStart(),
                        range.periodEnd());

        // 2. 정확한 매칭이 없으면 기간이 겹치는 스냅샷 조회 시도
        // 기간이 겹치는 조건: 스냅샷 시작일 <= 조회 종료일 AND 스냅샷 종료일 >= 조회 시작일
        if (snapshots.isEmpty()) {
            snapshots = snapshotRepository
                    .findByPeriodTypeAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByRankingAsc(
                            periodType,
                            range.periodEnd(), // periodStartDate <= 이 값 (조회 종료일)
                            range.periodStart()); // periodEndDate >= 이 값 (조회 시작일)
        }

        // 3. 그래도 없으면 가장 최근 스냅샷 조회 시도
        if (snapshots.isEmpty()) {
            snapshots = snapshotRepository
                    .findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc(periodType);
        }

        // 4. 모든 시도가 실패하면 새로 생성
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

        log.info("인기 게시글 스냅샷 생성 시작 - 기간: {} ~ {}, 카테고리: {}", startDateTime, endDateTime, TARGET_CATEGORY);

        // "자랑" 또는 "PRIDE" 카테고리 게시글 조회 (레거시 호환)
        List<Board> prideBoards = boardRepository.findByCategoryAndCreatedAtBetween(
                TARGET_CATEGORY,
                startDateTime,
                endDateTime);

        // "자랑"으로 조회했는데 없으면 "PRIDE"로도 시도 (레거시 데이터 호환)
        if (prideBoards.isEmpty() && !TARGET_CATEGORY.equals("PRIDE")) {
            List<Board> prideLegacyBoards = boardRepository.findByCategoryAndCreatedAtBetween(
                    "PRIDE",
                    startDateTime,
                    endDateTime);
            prideBoards = prideLegacyBoards;
            log.info("'자랑' 카테고리로 조회 실패, 'PRIDE' 카테고리로 재조회 시도");
        }

        log.info("조회된 자랑 카테고리 게시글 수: {}", prideBoards.size());

        if (prideBoards.isEmpty()) {
            log.warn("자랑 카테고리 게시글이 없거나 기간 내 게시글이 없습니다. 스냅샷을 생성하지 않습니다.");
            return List.of();
        }

        // Board ID 목록 추출
        List<Long> boardIds = prideBoards.stream()
                .map(Board::getIdx)
                .collect(Collectors.toList());

        // 배치 조회로 실시간 집계 (동시성 문제 해결)
        Map<Long, Integer> likeCountsMap = getLikeCountsBatch(boardIds);
        Map<Long, Integer> commentCountsMap = getCommentCountsBatch(boardIds);
        Map<Long, Integer> viewCountsMap = getViewCountsBatch(boardIds);

        List<BoardScore> rankedBoards = prideBoards.stream()
                .map(board -> {
                    int likes = likeCountsMap.getOrDefault(board.getIdx(), 0);
                    int comments = commentCountsMap.getOrDefault(board.getIdx(), 0);
                    int views = viewCountsMap.getOrDefault(board.getIdx(), 0);
                    int score = calculatePopularityScore(likes, comments, views);
                    return new BoardScore(board, score, likes, comments, views);
                })
                .sorted(Comparator.comparingInt(BoardScore::score).reversed()
                        .thenComparing(bs -> bs.board().getCreatedAt(), Comparator.reverseOrder()))
                .limit(30)
                .collect(Collectors.toList());

        log.info("기존 스냅샷 삭제 시작 - periodType: {}, startDate: {}, endDate: {}",
                periodType, range.periodStart(), range.periodEnd());
        snapshotRepository.deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate(
                periodType,
                range.periodStart(),
                range.periodEnd());

        List<BoardPopularitySnapshot> snapshots = createSnapshots(periodType, range, rankedBoards);
        log.info("생성할 스냅샷 수: {}", snapshots.size());

        List<BoardPopularitySnapshot> saved = snapshotRepository.saveAll(snapshots);
        log.info("스냅샷 저장 완료 - 저장된 수: {}", saved.size());

        return saved;
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
                        .likeCount(score.likes())
                        .commentCount(score.comments())
                        .viewCount(score.views())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 인기도 점수 계산 (실시간 집계된 값 사용)
     */
    private int calculatePopularityScore(int likes, int comments, int views) {
        return (likes * 3) + (comments * 2) + views;
    }

    /**
     * 여러 게시글의 좋아요 카운트를 배치로 조회 (실시간 집계)
     * IN 절 크기 제한을 위해 배치 단위로 나누어 조회
     */
    private Map<Long, Integer> getLikeCountsBatch(List<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return new HashMap<>();
        }

        // IN 절 크기 제한 (일반적으로 1000개 이하 권장)
        final int BATCH_SIZE = 1000;
        Map<Long, Integer> countsMap = new HashMap<>();

        // boardIds를 배치 단위로 나누어 처리
        for (int i = 0; i < boardIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, boardIds.size());
            List<Long> batch = boardIds.subList(i, end);

            List<Object[]> results = boardReactionRepository.countByBoardsGroupByReactionType(batch);

            for (Object[] result : results) {
                Long boardId = ((Number) result[0]).longValue();
                ReactionType reactionType = (ReactionType) result[1];
                Long count = ((Number) result[2]).longValue();

                if (reactionType == ReactionType.LIKE) {
                    countsMap.put(boardId, count.intValue());
                }
            }
        }

        // 좋아요가 없는 게시글은 0으로 초기화
        boardIds.forEach(id -> countsMap.putIfAbsent(id, 0));

        return countsMap;
    }

    /**
     * 여러 게시글의 댓글 카운트를 배치로 조회 (실시간 집계)
     * IN 절 크기 제한을 위해 배치 단위로 나누어 조회
     */
    private Map<Long, Integer> getCommentCountsBatch(List<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return new HashMap<>();
        }

        // IN 절 크기 제한 (일반적으로 1000개 이하 권장)
        final int BATCH_SIZE = 1000;
        Map<Long, Integer> countsMap = new HashMap<>();

        // boardIds를 배치 단위로 나누어 처리
        for (int i = 0; i < boardIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, boardIds.size());
            List<Long> batch = boardIds.subList(i, end);

            List<Object[]> results = commentRepository.countByBoardsAndIsDeletedFalse(batch);

            for (Object[] result : results) {
                Long boardId = ((Number) result[0]).longValue();
                Long count = ((Number) result[1]).longValue();
                countsMap.put(boardId, count.intValue());
            }
        }

        // 댓글이 없는 게시글은 0으로 초기화
        boardIds.forEach(id -> countsMap.putIfAbsent(id, 0));

        return countsMap;
    }

    /**
     * 여러 게시글의 조회수 카운트를 배치로 조회 (실시간 집계)
     * IN 절 크기 제한을 위해 배치 단위로 나누어 조회
     */
    private Map<Long, Integer> getViewCountsBatch(List<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return new HashMap<>();
        }

        // IN 절 크기 제한 (일반적으로 1000개 이하 권장)
        final int BATCH_SIZE = 1000;
        Map<Long, Integer> countsMap = new HashMap<>();

        // boardIds를 배치 단위로 나누어 처리
        for (int i = 0; i < boardIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, boardIds.size());
            List<Long> batch = boardIds.subList(i, end);

            List<Object[]> results = boardViewLogRepository.countByBoards(batch);

            for (Object[] result : results) {
                Long boardId = ((Number) result[0]).longValue();
                Long count = ((Number) result[1]).longValue();
                countsMap.put(boardId, count.intValue());
            }
        }

        // 조회수가 없는 게시글은 0으로 초기화
        boardIds.forEach(id -> countsMap.putIfAbsent(id, 0));

        return countsMap;
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

    /**
     * record타입은 Java 16+ 불변 데이터 캐리어. 생성자/Getter/equals/hashCode/toString 자동 생성.
     */

    /**
     * 인기글 집계 기간 범위
     * 
     * @param periodStart 집계 시작일 (포함)
     * @param periodEnd   집계 종료일 (포함)
     */
    private record PeriodRange(LocalDate periodStart, LocalDate periodEnd) {
    }

    /**
     * 게시글 인기도 점수 및 구성 요소
     *
     * @param board    게시글 엔티티
     * @param score    종합 인기도 점수
     * @param likes    좋아요 수
     * @param comments 댓글 수
     * @param views    조회수
     */
    private record BoardScore(Board board, int score, int likes, int comments, int views) {
    }
}
