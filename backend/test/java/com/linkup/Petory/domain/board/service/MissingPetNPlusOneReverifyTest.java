package com.linkup.Petory.domain.board.service;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.dto.MissingPetBoardDTO;
import com.linkup.Petory.domain.board.dto.MissingPetBoardPageResponseDTO;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * MissingPet 목록 N+1 재검증 (2026-07-12)
 * ====================================================================================
 *
 * troubleshooting/missing-pet/performance-measurement-results.md (207 → 3 queries)의
 * 수치를 다시 실행해 재현성을 확인한다.
 *
 * - Before: 문서가 기술한 "해결 전 코드"를 재현 (JOIN FETCH 없이 조회 → 댓글수·파일
 *   개별 조회)
 * - After: 실제 프로덕션 경로 그대로 호출
 *   (MissingPetBoardService.getBoardsWithPaging())
 *
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class MissingPetNPlusOneReverifyTest {

    @Autowired
    private MissingPetBoardRepository missingPetBoardRepository;

    @Autowired
    private MissingPetCommentService missingPetCommentService;

    @Autowired
    private AttachmentFileService attachmentFileService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private MissingPetBoardService missingPetBoardService;

    @PersistenceContext
    private EntityManager entityManager;

    private Users author;
    private List<MissingPetBoard> testBoards;

    private static final int BOARD_COUNT = 100;

    @BeforeEach
    void setUp() {
        author = usersRepository.save(Users.builder()
                .id("missingpet_author")
                .username("missingpet_author")
                .email("missingpet_author@test.com")
                .nickname("실종제보작성자")
                .password("password")
                .role(Role.USER)
                .build());

        testBoards = new ArrayList<>();
        for (int i = 0; i < BOARD_COUNT; i++) {
            MissingPetBoard board = MissingPetBoard.builder()
                    .user(author)
                    .title("실종 제보 " + i)
                    .content("내용 " + i)
                    .petName("펫" + i)
                    .species("DOG")
                    .isDeleted(false)
                    .build();
            board = missingPetBoardRepository.save(board);
            testBoards.add(board);

            // 게시글당 댓글 3개 (댓글수 조회 lazy N+1 재현용)
            for (int c = 0; c < 3; c++) {
                entityManager.persist(MissingPetComment.builder()
                        .board(board)
                        .user(author)
                        .content("댓글 " + c)
                        .isDeleted(false)
                        .build());
            }

            // 게시글당 첨부파일 1개 (File N+1 재현용)
            entityManager.persist(AttachmentFile.builder()
                    .targetType(FileTargetType.MISSING_PET)
                    .targetIdx(board.getIdx())
                    .filePath("missing_" + i + ".jpg")
                    .fileType("image/jpeg")
                    .build());
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("MissingPet 목록: N+1(해결 전 재현) vs 실제 프로덕션 경로 비교")
    void reproduceNPlusOneVsProductionPath() {
        Statistics stats = getStatistics();
        stats.clear();

        // ===== Before: 문서가 기술한 해결 전 코드 재현 =====
        entityManager.flush();
        entityManager.clear();
        System.gc();

        long beforeStart = System.currentTimeMillis();
        List<MissingPetBoardDTO> beforeResult = getBoardsWithIndividualQueries();
        long beforeElapsed = System.currentTimeMillis() - beforeStart;
        long beforeQueryCount = stats.getQueryExecutionCount();

        System.out.println("\n[Before] JOIN FETCH 없음 + 댓글수/파일 개별 조회");
        System.out.println("  쿼리 수: " + beforeQueryCount);
        System.out.println("  실행 시간: " + beforeElapsed + " ms");

        stats.clear();
        entityManager.flush();
        entityManager.clear();
        System.gc();

        // ===== After: 실제 프로덕션 경로 그대로 =====
        long afterStart = System.currentTimeMillis();
        MissingPetBoardPageResponseDTO afterResult = missingPetBoardService
                .getBoardsWithPaging(null, 0, BOARD_COUNT);
        long afterElapsed = System.currentTimeMillis() - afterStart;
        long afterQueryCount = stats.getQueryExecutionCount();

        System.out.println("\n[After] 실제 프로덕션 경로 (getBoardsWithPaging)");
        System.out.println("  쿼리 수: " + afterQueryCount);
        System.out.println("  실행 시간: " + afterElapsed + " ms");

        System.out.println("\n=== 결과 ===");
        System.out.println("쿼리 수: " + beforeQueryCount + " → " + afterQueryCount
                + " (" + String.format("%.1f", (1 - (double) afterQueryCount / beforeQueryCount) * 100) + "% 감소)");
        System.out.println("실행 시간: " + beforeElapsed + "ms → " + afterElapsed + "ms");

        assertThat(beforeResult).hasSize(BOARD_COUNT);
        assertThat(afterResult.boards()).hasSize(BOARD_COUNT);
        assertThat(afterQueryCount).isLessThan(beforeQueryCount);
    }

    /**
     * 해결 전 코드 재현: JOIN FETCH 없이 조회 → 댓글수·파일 개별 조회 (원 문서의
     * "게시글 1 + 댓글 N + 파일 N" 패턴)
     */
    private List<MissingPetBoardDTO> getBoardsWithIndividualQueries() {
        TypedQuery<MissingPetBoard> query = entityManager.createQuery(
                "SELECT b FROM MissingPetBoard b WHERE b.isDeleted = false AND b.user.idx = :userId ORDER BY b.createdAt DESC",
                MissingPetBoard.class);
        query.setParameter("userId", author.getIdx());
        List<MissingPetBoard> boards = query.getResultList();

        List<MissingPetBoardDTO> results = new ArrayList<>();
        for (MissingPetBoard board : boards) {
            // 댓글 수 개별 조회 (N+1)
            int commentCount = missingPetCommentService.getCommentCount(board);
            // File 개별 조회 (N+1)
            var files = attachmentFileService.getAttachments(FileTargetType.MISSING_PET, board.getIdx());

            results.add(MissingPetBoardDTO.builder()
                    .idx(board.getIdx())
                    .title(board.getTitle())
                    .commentCount(commentCount)
                    .build());

            if (files.size() < 0) {
                throw new IllegalStateException("unreachable");
            }
        }
        return results;
    }

    private Statistics getStatistics() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }
}
