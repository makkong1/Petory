package com.linkup.Petory.domain.board.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.converter.BoardPopularitySnapshotConverter;
import com.linkup.Petory.domain.board.dto.BoardPopularitySnapshotDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.repository.BoardPopularitySnapshotRepository;
import com.linkup.Petory.domain.board.repository.BoardPopularitySnapshotSpecs;
import com.linkup.Petory.domain.board.repository.BoardReactionRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.BoardViewLogRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;

import lombok.RequiredArgsConstructor;
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

        // 2. 정확한 매칭이 없으면 기간이 겹치는 스냅샷 조회 시도 (Specification 사용)
        if (snapshots.isEmpty()) {
            snapshots = snapshotRepository.findAll(
                    BoardPopularitySnapshotSpecs.periodOverlaps(periodType, range.periodStart(), range.periodEnd()),
                    Sort.by("ranking").ascending());
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

        // 5. 스냅샷 생성도 실패(게시글 없음)하면 최신 게시글로 대체
        if (snapshots.isEmpty()) {
            return buildRecentBoardFallback(periodType, range);
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

        // [리팩토링] 배치 조회를 병렬 실행 (독립적인 3개 쿼리 → 응답 시간 단축)
        Map<Long, BoardCounts> countsMap = fetchBoardCountsInParallel(boardIds);

        // [리팩토링] BoardCounts 통합 DTO로 점수 계산
        List<BoardScore> rankedBoards = prideBoards.stream()
                .map(board -> {
                    BoardCounts counts = countsMap.getOrDefault(board.getIdx(), BoardCounts.ZERO);
                    int score = calculatePopularityScore(counts.likes(), counts.comments(), counts.views());
                    return new BoardScore(board, score, counts.likes(), counts.comments(), counts.views());
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
     * [리팩토링] 3개 배치 조회를 병렬 실행 후 Map<Long, BoardCounts>로 통합
     *
     * <p>
     * 동작: 좋아요/댓글/조회수 3개 쿼리를 supplyAsync로 동시(병렬) 실행 → allOf로 대기
     * → 결과를 BoardCounts로 합침
     */
    private Map<Long, BoardCounts> fetchBoardCountsInParallel(List<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return Map.of();
        }

        /*
         * CompletableFuture<Map<Long, Integer>>
         * "나중에 Map<Long, Integer> 결과가 들어올 비동기 작업 객체"
         * supplyAsync()
         * = 값을 반환하는 작업을 별도 스레드에서 비동기로 실행한다.
         * 아래 3개 줄이 실행되면 좋아요/댓글/조회수 조회가 각각 시작된다.
         * 즉, allOf()가 실행을 시작시키는 것이 아니라,
         * supplyAsync() 호출 시점에 이미 작업이 시작된다.
         */
        CompletableFuture<Map<Long, Integer>> likesFuture = CompletableFuture
                .supplyAsync(() -> getLikeCountsBatch(boardIds));
        CompletableFuture<Map<Long, Integer>> commentsFuture = CompletableFuture
                .supplyAsync(() -> getCommentCountsBatch(boardIds));
        CompletableFuture<Map<Long, Integer>> viewsFuture = CompletableFuture
                .supplyAsync(() -> getViewCountsBatch(boardIds));

        /*
         * allOf()
         * = 이미 시작된 3개 Future가 모두 완료될 때까지 기다리는 Future를 만든다.
         * thenApply()
         * = allOf()가 완료된 뒤, 즉 3개 조회가 모두 끝난 뒤 실행되는 후속 작업이다.
         * 여기서 allOf()는 조회를 다시 실행하지 않는다.
         * 단지 "3개가 전부 끝났는지"를 기다리는 역할이다.
         */
        CompletableFuture<Map<Long, BoardCounts>> combined = CompletableFuture
                .allOf(likesFuture, commentsFuture, viewsFuture)
                .thenApply(v -> {
                    Map<Long, Integer> likes = likesFuture.join();
                    Map<Long, Integer> comments = commentsFuture.join();
                    Map<Long, Integer> views = viewsFuture.join();

                    Map<Long, BoardCounts> result = new HashMap<>();
                    /*
                     * 조회 결과 Map에 특정 boardId가 없을 수 있다.
                     * 예: 좋아요가 0개인 게시글은 likes Map에 없을 수 있음.
                     * 그래서 getOrDefault(boardId, 0)으로 기본값 0을 넣는다.
                     */
                    for (Long boardId : boardIds) {
                        result.put(boardId, new BoardCounts(
                                likes.getOrDefault(boardId, 0),
                                comments.getOrDefault(boardId, 0),
                                views.getOrDefault(boardId, 0)));
                    }
                    return result;
                });

        /*
         * 
         * combined.join()
         * = 최종 통합 결과가 완성될 때까지 기다린 뒤 Map<Long, BoardCounts>를 반환한다.
         * 즉, 내부적으로는 병렬 조회를 사용하지만
         * 이 메서드 자체는 호출자에게 최종 결과를 반환하는 동기 메서드다.
         */
        return combined.join();
    }

    /**
     * [리팩토링] 여러 게시글의 좋아요 카운트를 배치로 조회 (실시간 집계, LIKE만 DB 조회)
     * IN 절 크기 제한을 위해 배치 단위로 나누어 조회
     */
    private Map<Long, Integer> getLikeCountsBatch(List<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return new HashMap<>();
        }

        final int BATCH_SIZE = 1000;
        Map<Long, Integer> countsMap = new HashMap<>();

        for (int i = 0; i < boardIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, boardIds.size());
            List<Long> batch = boardIds.subList(i, end);

            List<Object[]> results = boardReactionRepository.countByBoardsAndReactionType(batch, ReactionType.LIKE);

            for (Object[] result : results) {
                Long boardId = ((Number) result[0]).longValue();
                Long count = ((Number) result[1]).longValue();
                countsMap.put(boardId, count.intValue());
            }
        }

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
     * 인기글 집계 기간 범위
     * 
     * @param periodStart 집계 시작일 (포함)
     * @param periodEnd   집계 종료일 (포함)
     */
    private record PeriodRange(LocalDate periodStart, LocalDate periodEnd) {
    }

    /**
     * [리팩토링] 게시글별 좋아요/댓글/조회수 통합 DTO
     */
    private record BoardCounts(int likes, int comments, int views) {
        static final BoardCounts ZERO = new BoardCounts(0, 0, 0);
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

    private List<BoardPopularitySnapshotDTO> buildRecentBoardFallback(
            PopularityPeriodType periodType, PeriodRange range) {
        log.info("인기 스냅샷 없음 — 최신 게시글 10개로 대체");
        List<Board> recent = boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
                PageRequest.of(0, 10)).getContent();
        int[] rank = {1};
        return recent.stream()
                .map(b -> new BoardPopularitySnapshotDTO(
                        null,
                        b.getIdx(),
                        periodType,
                        range.periodStart(),
                        range.periodEnd(),
                        rank[0]++,
                        0,
                        0,
                        0,
                        b.getViewCount() != null ? b.getViewCount() : 0,
                        b.getTitle(),
                        b.getCategory(),
                        null,
                        b.getCreatedAt()))
                .collect(Collectors.toList());
    }
}
