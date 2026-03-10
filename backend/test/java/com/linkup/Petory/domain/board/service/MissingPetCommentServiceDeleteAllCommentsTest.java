package com.linkup.Petory.domain.board.service;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.board.entity.MissingPetGender;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MissingPetCommentService.deleteAllCommentsByBoard 메서드 테스트
 *
 * missing-pet-backend-performance-optimization.md 문서의
 * "deleteAllCommentsByBoard N건 루프 save" 이슈 검증용 테스트입니다.
 *
 * @Transactional로 자동 롤백되어 실제 DB에 영향 없음
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissingPetCommentServiceDeleteAllCommentsTest {

    @Autowired
    private MissingPetCommentService commentService;

    @Autowired
    private MissingPetBoardRepository boardRepository;

    @Autowired
    private MissingPetCommentRepository commentRepository;

    @Autowired
    private UsersRepository usersRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Users testUser;
    private MissingPetBoard testBoard;

    @BeforeEach
    void setUp() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        testUser = Users.builder()
                .id("delete_all_test_user_" + timestamp)
                .username("deletealltest_" + timestamp)
                .nickname("삭제테스트유저")
                .email("deleteall_" + timestamp + "@test.com")
                .password("password")
                .emailVerified(true)
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .build();
        testUser = usersRepository.save(testUser);

        testBoard = MissingPetBoard.builder()
                .user(testUser)
                .title("deleteAllCommentsByBoard 테스트 게시글")
                .content("테스트 내용")
                .petName("테스트펫")
                .species("강아지")
                .breed("골든리트리버")
                .gender(MissingPetGender.M)
                .age("3세")
                .color("골드")
                .lostDate(LocalDate.now())
                .lostLocation("테스트 위치")
                .latitude(new BigDecimal("37.5665"))
                .longitude(new BigDecimal("126.9780"))
                .status(MissingPetStatus.MISSING)
                .isDeleted(false)
                .build();
        testBoard = boardRepository.save(testBoard);

        boardRepository.flush();
    }

    @Test
    @DisplayName("댓글 N개 있을 때 deleteAllCommentsByBoard 호출 시 모두 소프트 삭제됨")
    void deleteAllCommentsByBoard_withMultipleComments_softDeletesAll() {
        // Given: 댓글 5개 생성
        for (int i = 1; i <= 5; i++) {
            MissingPetComment comment = MissingPetComment.builder()
                    .board(testBoard)
                    .user(testUser)
                    .content("테스트 댓글 " + i)
                    .address("테스트 주소 " + i)
                    .latitude(new BigDecimal("37.5665"))
                    .longitude(new BigDecimal("126.9780"))
                    .isDeleted(false)
                    .build();
            commentRepository.save(comment);
        }
        commentRepository.flush();

        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(5);

        // When: deleteAllCommentsByBoard 호출
        commentService.deleteAllCommentsByBoard(testBoard);

        // Then: 모든 댓글이 소프트 삭제됨
        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(0);

        List<MissingPetComment> allComments = commentRepository.findByBoardOrderByCreatedAtAsc(testBoard);
        assertThat(allComments).hasSize(5);
        assertThat(allComments).allMatch(c -> Boolean.TRUE.equals(c.getIsDeleted()));
        assertThat(allComments).allMatch(c -> c.getDeletedAt() != null);
    }

    @Test
    @DisplayName("댓글 0개일 때 deleteAllCommentsByBoard 호출 시 예외 없이 동작")
    void deleteAllCommentsByBoard_withNoComments_doesNotThrow() {
        // Given: 댓글 없음
        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(0);

        // When & Then: 예외 없이 실행
        commentService.deleteAllCommentsByBoard(testBoard);

        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(0);
    }

    @Test
    @DisplayName("댓글 1개 있을 때 deleteAllCommentsByBoard 호출 시 정상 소프트 삭제")
    void deleteAllCommentsByBoard_withSingleComment_softDeletes() {
        // Given: 댓글 1개
        MissingPetComment comment = MissingPetComment.builder()
                .board(testBoard)
                .user(testUser)
                .content("단일 댓글")
                .address("테스트 주소")
                .latitude(new BigDecimal("37.5665"))
                .longitude(new BigDecimal("126.9780"))
                .isDeleted(false)
                .build();
        commentRepository.save(comment);
        commentRepository.flush();

        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(1);

        // When
        commentService.deleteAllCommentsByBoard(testBoard);

        // Then
        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(0);
        List<MissingPetComment> deleted = commentRepository.findByBoardOrderByCreatedAtAsc(testBoard);
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).getIsDeleted()).isTrue();
        assertThat(deleted.get(0).getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 삭제된 댓글은 findByBoardAndIsDeletedFalse에 포함되지 않음 - deleteAllCommentsByBoard는 미삭제 댓글만 대상")
    void deleteAllCommentsByBoard_onlyAffectsNonDeletedComments() {
        // Given: 삭제된 댓글 1개 + 미삭제 댓글 2개
        MissingPetComment deletedComment = MissingPetComment.builder()
                .board(testBoard)
                .user(testUser)
                .content("이미 삭제된 댓글")
                .isDeleted(true)
                .deletedAt(java.time.LocalDateTime.now().minusDays(1))
                .build();
        commentRepository.save(deletedComment);

        for (int i = 1; i <= 2; i++) {
            MissingPetComment comment = MissingPetComment.builder()
                    .board(testBoard)
                    .user(testUser)
                    .content("미삭제 댓글 " + i)
                    .isDeleted(false)
                    .build();
            commentRepository.save(comment);
        }
        commentRepository.flush();

        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(2);

        // When
        commentService.deleteAllCommentsByBoard(testBoard);

        // Then: 미삭제 2개만 소프트 삭제됨
        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(0);
        List<MissingPetComment> all = commentRepository.findByBoardOrderByCreatedAtAsc(testBoard);
        assertThat(all).hasSize(3);
        assertThat(all).allMatch(c -> Boolean.TRUE.equals(c.getIsDeleted()));
    }

    @Test
    @DisplayName("댓글 30개 시 전후 비교: 이전(N건 루프 save) vs 최적화(배치 UPDATE)")
    void deleteAllCommentsByBoard_with30Comments_beforeAfterComparison() {
        int commentCount = 30;

        // ========== [1] 이전 방식: N건 루프 save (시뮬레이션) ==========
        createComments(commentCount);
        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        // 이전 구현: SELECT 후 루프마다 save()
        List<MissingPetComment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(testBoard);
        for (MissingPetComment c : comments) {
            c.setIsDeleted(true);
            c.setDeletedAt(LocalDateTime.now());
            commentRepository.save(c);
        }
        entityManager.flush();

        long oldQueryCount = stats.getPrepareStatementCount();

        // 롤백 후 재설정 (다음 단계를 위해)
        entityManager.clear();
        stats.clear();

        // ========== [2] 최적화 방식: 배치 UPDATE (현재 서비스) ==========
        // @Transactional로 setUp 데이터가 롤백되어 testBoard/댓글 초기화됨 - 새 트랜잭션에서 다시 생성
        createComments(commentCount);
        entityManager.flush();
        entityManager.clear();
        stats.clear();

        commentService.deleteAllCommentsByBoard(testBoard);
        entityManager.flush();

        long newQueryCount = stats.getPrepareStatementCount();

        // ========== 결과 출력 ==========
        System.out.println("\n" + "=".repeat(60));
        System.out.println("deleteAllCommentsByBoard - 전후 쿼리 수 비교 (댓글 30개)");
        System.out.println("=".repeat(60));
        System.out.println("[이전] N건 루프 save: 1 SELECT + 30 UPDATE = 31 쿼리");
        System.out.println("       실제 측정값: " + oldQueryCount + " 쿼리");
        System.out.println("-".repeat(60));
        System.out.println("[최적화] 배치 UPDATE: 1 UPDATE");
        System.out.println("         실제 측정값: " + newQueryCount + " 쿼리");
        System.out.println("=".repeat(60));
        System.out.println("개선: " + oldQueryCount + " → " + newQueryCount + " (" + (oldQueryCount - newQueryCount) + " 쿼리 감소)");
        System.out.println("=".repeat(60) + "\n");

        assertThat(commentService.getCommentCount(testBoard)).isEqualTo(0);
        assertThat(newQueryCount).isLessThan(oldQueryCount);
    }

    private void createComments(int count) {
        for (int i = 1; i <= count; i++) {
            MissingPetComment comment = MissingPetComment.builder()
                    .board(testBoard)
                    .user(testUser)
                    .content("테스트 댓글 " + i)
                    .address("테스트 주소 " + i)
                    .latitude(new BigDecimal("37.5665"))
                    .longitude(new BigDecimal("126.9780"))
                    .isDeleted(false)
                    .build();
            commentRepository.save(comment);
        }
        commentRepository.flush();
    }
}
