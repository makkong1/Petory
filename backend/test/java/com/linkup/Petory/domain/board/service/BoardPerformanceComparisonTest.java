package com.linkup.Petory.domain.board.service;

import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.repository.BoardReactionRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * Board 도메인 성능 최적화 전후 비교 테스트
 * ====================================================================================
 * 
 * 이 테스트는 최적화 전후의 성능 차이를 측정합니다.
 * 
 * 📌 실행 방법:
 * 1. IDE에서 원하는 테스트 메서드 우클릭 → Run
 * 2. 또는 전체 테스트 실행: ./gradlew test --tests BoardPerformanceComparisonTest
 * 
 * 📊 측정 항목:
 * - 쿼리 수 (Hibernate Statistics 사용)
 * - 실행 시간 (밀리초)
 * - 메모리 사용량 (MB)
 * 
 * ✅ 테스트 항목:
 * 1. testBatchReactionQueryOptimization() - 배치 조회 vs 개별 조회 비교
 * 2. testFetchJoinOptimization() - Fetch Join vs LAZY 로딩 비교
 * 3. testOverallPerformanceComparison() - 전체 성능 비교 (추천!)
 * 
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class BoardPerformanceComparisonTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private BoardReactionRepository boardReactionRepository;

    @Autowired
    private UsersRepository usersRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Users testUser;
    private List<Users> testUsers; // 반응을 남길 여러 사용자
    private List<Board> testBoards;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성 (게시글 작성자)
        testUser = Users.builder()
                .id("testuser") // 필수 필드
                .username("testuser")
                .email("test@test.com")
                .nickname("테스트유저")
                .password("password")
                .role(Role.USER) // 필수 필드
                .build();
        testUser = usersRepository.save(testUser);

        // 반응을 남길 사용자들 생성 (각 게시글당 좋아요 5개, 싫어요 2개 = 총 7명 필요)
        testUsers = new ArrayList<>();
        for (int i = 0; i < 10; i++) { // 여유있게 10명 생성
            Users user = Users.builder()
                    .id("testuser" + i)
                    .username("testuser" + i)
                    .email("test" + i + "@test.com")
                    .nickname("테스트유저" + i)
                    .password("password")
                    .role(Role.USER)
                    .build();
            testUsers.add(usersRepository.save(user));
        }

        // 테스트 게시글 100개 생성
        testBoards = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Board board = Board.builder()
                    .title("테스트 게시글 " + i)
                    .content("테스트 내용 " + i)
                    .category("자유")
                    .user(testUser)
                    .isDeleted(false)
                    .build();
            testBoards.add(board);
        }
        testBoards = boardRepository.saveAll(testBoards);

        // 각 게시글에 좋아요/싫어요 추가 (N+1 문제 재현을 위해)
        // 각 반응마다 다른 사용자 사용 (unique constraint 위반 방지)
        int userIndex = 0;
        for (Board board : testBoards) {
            // 좋아요 5개 (각각 다른 사용자)
            for (int i = 0; i < 5; i++) {
                BoardReaction reaction = BoardReaction.builder()
                        .board(board)
                        .user(testUsers.get(userIndex % testUsers.size())) // 사용자 순환 사용
                        .reactionType(ReactionType.LIKE)
                        .build();
                boardReactionRepository.save(reaction);
                userIndex++;
            }
            // 싫어요 2개 (각각 다른 사용자)
            for (int i = 0; i < 2; i++) {
                BoardReaction reaction = BoardReaction.builder()
                        .board(board)
                        .user(testUsers.get(userIndex % testUsers.size())) // 사용자 순환 사용
                        .reactionType(ReactionType.DISLIKE)
                        .build();
                boardReactionRepository.save(reaction);
                userIndex++;
            }
        }

        // 영속성 컨텍스트 초기화
        entityManager.clear();
    }

    /**
     * ====================================================================================
     * 테스트 1: 배치 조회 vs 개별 조회 성능 비교
     * ====================================================================================
     * 
     * 📌 목적: N+1 문제 해결 전후 비교
     * - 최적화 전: 각 게시글마다 좋아요/싫어요 카운트를 개별 쿼리로 조회 (N+1 문제)
     * - 최적화 후: 모든 게시글의 반응 정보를 배치로 한 번에 조회
     * 
     * 📊 예상 결과:
     * - 쿼리 수: 201개 → 3개 (99% 감소)
     * - 실행 시간: ~30초 → ~0.3초 (100배 개선)
     * 
     * ====================================================================================
     */
    @Test
    @DisplayName("배치 조회로 반응 정보 조회 최적화 전후 비교")
    void testBatchReactionQueryOptimization() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📌 테스트 1: 배치 조회 vs 개별 조회 성능 비교");
        System.out.println("=".repeat(80));

        // Hibernate Statistics 초기화
        Statistics stats = getStatistics();
        stats.clear();

        // ========== [1단계] 최적화 전: 개별 조회 (N+1 문제 재현) ==========
        System.out.println("\n[1단계] 최적화 전: 개별 조회 방식 (N+1 문제)");
        System.out.println("→ 각 게시글마다 좋아요/싫어요 카운트를 개별 쿼리로 조회");
        System.out.println("→ 예상: 1개(게시글) + 100개(좋아요) + 100개(싫어요) = 201개 쿼리\n");

        long beforeMemory = getUsedMemory();
        long beforeTime = System.currentTimeMillis();

        getAllBoardsWithIndividualQueries(); // ← N+1 문제가 있는 방식

        long afterTime = System.currentTimeMillis();
        long afterMemory = getUsedMemory();
        long beforeElapsed = afterTime - beforeTime;
        long beforeMemoryUsed = afterMemory - beforeMemory;
        long beforeQueryCount = stats.getQueryExecutionCount();

        System.out.println("📊 결과:");
        System.out.println("  ⏱️  실행 시간: " + beforeElapsed + " ms");
        System.out.println("  📊 쿼리 수: " + beforeQueryCount + " 개");
        System.out.println("  💾 메모리 사용량: " + (beforeMemoryUsed / (1024 * 1024)) + " MB");

        // Statistics 초기화
        stats.clear();
        entityManager.clear();

        // 메모리 측정 전 GC 실행
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ========== [2단계] 최적화 후: 배치 조회 ==========
        System.out.println("\n[2단계] 최적화 후: 배치 조회 방식");
        System.out.println("→ 모든 게시글의 반응 정보를 배치로 한 번에 조회");
        System.out.println("→ 예상: 1개(게시글) + 1개(반응 배치) + 1개(첨부파일) = 3개 쿼리\n");

        beforeMemory = getUsedMemory();
        beforeTime = System.currentTimeMillis();

        List<BoardDTO> afterResults = boardService.getAllBoards("ALL"); // ← 최적화된 방식

        afterTime = System.currentTimeMillis();
        afterMemory = getUsedMemory();
        long afterElapsed = afterTime - beforeTime;
        long afterMemoryUsed = afterMemory - beforeMemory;
        long afterQueryCount = stats.getQueryExecutionCount();

        System.out.println("📊 결과:");
        System.out.println("  ⏱️  실행 시간: " + afterElapsed + " ms");
        System.out.println("  📊 쿼리 수: " + afterQueryCount + " 개");
        System.out.println("  💾 메모리 사용량: " + (afterMemoryUsed / (1024 * 1024)) + " MB");

        // ========== [3단계] 결과 비교 ==========
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🎯 성능 개선 결과");
        System.out.println("=".repeat(80));
        System.out.println("📊 쿼리 수: " + beforeQueryCount + " 개 → " + afterQueryCount + " 개");
        System.out.println(
                "   → " + String.format("%.2f", (1 - (double) afterQueryCount / beforeQueryCount) * 100) + "% 감소");
        System.out.println("⏱️  실행 시간: " + beforeElapsed + " ms → " + afterElapsed + " ms");
        System.out.println("   → " + String.format("%.2f", (double) beforeElapsed / afterElapsed) + "배 개선");
        System.out.println("💾 메모리 사용량: " + (beforeMemoryUsed / (1024 * 1024)) + " MB → " +
                (afterMemoryUsed / (1024 * 1024)) + " MB");
        System.out.println("=".repeat(80));

        // 검증
        assertThat(afterResults).hasSize(100);
        assertThat(afterQueryCount).isLessThan(beforeQueryCount);
        assertThat(afterElapsed).isLessThan(beforeElapsed);
    }

    /**
     * ====================================================================================
     * 테스트 2: Fetch Join vs LAZY 로딩 성능 비교
     * ====================================================================================
     * 
     * 📌 목적: 작성자 정보 조회 최적화 전후 비교
     * - 최적화 전: LAZY 로딩으로 각 게시글마다 작성자 정보를 개별 쿼리로 조회 (N+1 문제)
     * - 최적화 후: Fetch Join으로 작성자 정보를 한 번에 조회
     * 
     * 📊 예상 결과:
     * - 쿼리 수: 101개 → 1개 (99% 감소)
     * 
     * ====================================================================================
     */
    @Test
    @DisplayName("Fetch Join으로 작성자 정보 조회 최적화 전후 비교")
    void testFetchJoinOptimization() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📌 테스트 2: Fetch Join vs LAZY 로딩 성능 비교");
        System.out.println("=".repeat(80));

        Statistics stats = getStatistics();
        stats.clear();

        // ========== [1단계] 최적화 전: LAZY 로딩 (N+1 문제) ==========
        System.out.println("\n[1단계] 최적화 전: LAZY 로딩 방식");
        System.out.println("→ Fetch Join 없이 조회 → 작성자 정보 접근 시 개별 쿼리 발생");
        System.out.println("→ 예상: 1개(게시글) + 100개(작성자) = 101개 쿼리\n");

        long beforeTime = System.currentTimeMillis();

        // Fetch Join 없이 조회 (LAZY 로딩 발생)
        List<Board> boardsWithoutFetch = boardRepository.findAll();

        // 작성자 정보 접근 시 추가 쿼리 발생
        for (Board board : boardsWithoutFetch) {
            board.getUser().getUsername(); // N+1 발생
        }

        long afterTime = System.currentTimeMillis();
        long beforeElapsed = afterTime - beforeTime;
        long beforeQueryCount = stats.getQueryExecutionCount();

        System.out.println("📊 결과:");
        System.out.println("  ⏱️  실행 시간: " + beforeElapsed + " ms");
        System.out.println("  📊 쿼리 수: " + beforeQueryCount + " 개 (1개 게시글 + " + (beforeQueryCount - 1) + "개 작성자 조회)");

        // Statistics 초기화
        stats.clear();
        entityManager.clear();

        // ========== [2단계] 최적화 후: Fetch Join ==========
        System.out.println("\n[2단계] 최적화 후: Fetch Join 방식");
        System.out.println("→ JOIN FETCH로 작성자 정보를 한 번에 조회");
        System.out.println("→ 예상: 1개 쿼리 (게시글 + 작성자 함께 조회)\n");

        beforeTime = System.currentTimeMillis();

        // Fetch Join으로 조회
        List<Board> boardsWithFetch = boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc();

        // 작성자 정보 접근 (추가 쿼리 없음)
        for (Board board : boardsWithFetch) {
            board.getUser().getUsername(); // 추가 쿼리 없음
        }

        afterTime = System.currentTimeMillis();
        long afterElapsed = afterTime - beforeTime;
        long afterQueryCount = stats.getQueryExecutionCount();

        System.out.println("📊 결과:");
        System.out.println("  ⏱️  실행 시간: " + afterElapsed + " ms");
        System.out.println("  📊 쿼리 수: " + afterQueryCount + " 개");

        // ========== [3단계] 결과 비교 ==========
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🎯 성능 개선 결과");
        System.out.println("=".repeat(80));
        System.out.println("📊 쿼리 수: " + beforeQueryCount + " 개 → " + afterQueryCount + " 개");
        System.out.println(
                "   → " + String.format("%.2f", (1 - (double) afterQueryCount / beforeQueryCount) * 100) + "% 감소");
        System.out.println("⏱️  실행 시간: " + beforeElapsed + " ms → " + afterElapsed + " ms");
        System.out.println("   → " + String.format("%.2f", (double) beforeElapsed / afterElapsed) + "배 개선");
        System.out.println("=".repeat(80));

        // 검증
        assertThat(boardsWithFetch).hasSize(100);
        assertThat(afterQueryCount).isLessThan(beforeQueryCount);
    }

    /**
     * ====================================================================================
     * 테스트 3: 전체 성능 비교 (실제 사용 시나리오) ⭐ 추천!
     * ====================================================================================
     * 
     * 📌 목적: 실제 사용 시나리오에서의 전체 성능 비교
     * - 최적화 전: 개별 조회 + LAZY 로딩 (N+1 문제 발생)
     * - 최적화 후: 배치 조회 + Fetch Join (최적화 적용)
     * 
     * 📊 측정 항목:
     * - 쿼리 수
     * - 실행 시간
     * - 메모리 사용량
     * 
     * ✅ 이 테스트 하나만 실행해도 전체 성능 개선 효과를 확인할 수 있습니다!
     * 
     * ====================================================================================
     */
    @Test
    @DisplayName("전체 성능 비교 - 실제 사용 시나리오 (추천!)")
    void testOverallPerformanceComparison() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("⭐ 테스트 3: 전체 성능 비교 (실제 사용 시나리오) - 추천!");
        System.out.println("=".repeat(80));

        Statistics stats = getStatistics();
        if (!stats.isStatisticsEnabled()) {
            System.out.println("⚠️  경고: Hibernate Statistics가 비활성화되어 있습니다!");
            System.out.println("   application.properties에서 hibernate.generate_statistics=true를 활성화하세요.");
        }
        stats.clear();

        // ========== [1단계] 최적화 전: 개별 조회 + LAZY 로딩 ==========
        System.out.println("\n[1단계] 최적화 전: 개별 조회 + LAZY 로딩");
        System.out.println("→ 각 게시글마다 반응 정보를 개별 쿼리로 조회");
        System.out.println("→ 작성자 정보도 LAZY 로딩으로 개별 쿼리 발생");
        System.out.println("→ 예상: 201개 이상의 쿼리\n");

        // 메모리 측정 전 GC 실행
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long beforeMemory = getUsedMemory();
        long beforeTime = System.currentTimeMillis();

        getAllBoardsWithIndividualQueries(); // ← N+1 문제가 있는 방식

        long afterTime = System.currentTimeMillis();
        long afterMemory = getUsedMemory();
        long beforeElapsed = afterTime - beforeTime;
        long beforeMemoryUsed = afterMemory - beforeMemory;
        long beforeQueryCount = stats.getQueryExecutionCount();

        System.out.println("📊 결과:");
        System.out.println("  ⏱️  실행 시간: " + beforeElapsed + " ms");
        System.out.println("  📊 쿼리 수: " + beforeQueryCount + " 개");
        System.out.println("  💾 메모리 사용량: " + (beforeMemoryUsed / (1024 * 1024)) + " MB");

        // Statistics 초기화
        stats.clear();
        entityManager.clear();

        // 메모리 측정 전 GC 실행
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ========== [2단계] 최적화 후: 배치 조회 + Fetch Join ==========
        System.out.println("\n[2단계] 최적화 후: 배치 조회 + Fetch Join");
        System.out.println("→ 반응 정보를 배치로 한 번에 조회");
        System.out.println("→ 작성자 정보도 Fetch Join으로 함께 조회");
        System.out.println("→ 예상: 3개 이하의 쿼리\n");

        beforeMemory = getUsedMemory();
        beforeTime = System.currentTimeMillis();

        List<BoardDTO> afterResults = boardService.getAllBoards("ALL"); // ← 최적화된 방식

        afterTime = System.currentTimeMillis();
        afterMemory = getUsedMemory();
        long afterElapsed = afterTime - beforeTime;
        long afterMemoryUsed = afterMemory - beforeMemory;
        long afterQueryCount = stats.getQueryExecutionCount();

        System.out.println("📊 결과:");
        System.out.println("  ⏱️  실행 시간: " + afterElapsed + " ms");
        System.out.println("  📊 쿼리 수: " + afterQueryCount + " 개");
        System.out.println("  💾 메모리 사용량: " + (afterMemoryUsed / (1024 * 1024)) + " MB");

        // ========== [3단계] 최종 결과 ==========
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🎯 최종 성능 개선 결과");
        System.out.println("=".repeat(80));
        System.out.println("📊 쿼리 수: " + beforeQueryCount + " 개 → " + afterQueryCount + " 개");
        System.out.println(
                "   → " + String.format("%.2f", (1 - (double) afterQueryCount / beforeQueryCount) * 100) + "% 감소");
        System.out.println("⏱️  실행 시간: " + beforeElapsed + " ms → " + afterElapsed + " ms");
        System.out.println("   → " + String.format("%.2f", (double) beforeElapsed / afterElapsed) + "배 개선");
        System.out.println("💾 메모리 사용량: " + (beforeMemoryUsed / (1024 * 1024)) + " MB → " +
                (afterMemoryUsed / (1024 * 1024)) + " MB");
        System.out.println("=".repeat(80));

        // 검증
        assertThat(afterResults).hasSize(100);
        assertThat(afterQueryCount).isLessThan(beforeQueryCount);
        assertThat(afterElapsed).isLessThan(beforeElapsed);
    }

    // ========== 헬퍼 메서드 ==========

    // ====================================================================================
    // 헬퍼 메서드
    // ====================================================================================

    /**
     * 최적화 전 방식: 개별 조회 (N+1 문제 재현)
     * 
     * 이 메서드는 최적화 전의 비효율적인 방식을 시뮬레이션합니다.
     * 각 게시글마다 좋아요/싫어요 카운트를 개별 쿼리로 조회하여 N+1 문제를 재현합니다.
     */
    private List<BoardDTO> getAllBoardsWithIndividualQueries() {
        List<Board> boards = boardRepository.findAll();

        return boards.stream()
                .map(board -> {
                    BoardDTO dto = BoardDTO.builder()
                            .idx(board.getIdx())
                            .title(board.getTitle())
                            .content(board.getContent())
                            .build();

                    // N+1 발생: 각 게시글마다 개별 쿼리
                    long likeCount = boardReactionRepository.countByBoardAndReactionType(
                            board, ReactionType.LIKE);
                    long dislikeCount = boardReactionRepository.countByBoardAndReactionType(
                            board, ReactionType.DISLIKE);

                    dto.setLikes((int) likeCount);
                    dto.setDislikes((int) dislikeCount);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Hibernate Statistics 가져오기
     */
    private Statistics getStatistics() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        return sessionFactory.getStatistics();
    }

    /**
     * 현재 사용 중인 메모리 (bytes)
     * GC를 강제 실행하여 정확한 측정을 위해 사용
     */
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        // GC 강제 실행으로 정확한 메모리 측정
        System.gc();
        try {
            Thread.sleep(100); // GC 완료 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
