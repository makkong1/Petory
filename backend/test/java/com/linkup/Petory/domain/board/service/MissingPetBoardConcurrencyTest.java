package com.linkup.Petory.domain.board.service;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MissingPetBoard 도메인의 동시성 문제 설명 테스트
 * 
 * 이 테스트는 potential-issues.md 문서에서 언급된 문제를 설명합니다:
 * - 게시글 삭제 시 댓글 Soft Delete 동시성 문제
 * - board.getComments()는 영속성 컨텍스트에 로드된 댓글만 포함
 * - 트랜잭션 중간에 다른 사용자가 댓글을 추가하면 해당 댓글은 삭제되지 않음
 * 
 * 주의: 같은 트랜잭션 내에서는 영속성 컨텍스트가 자동으로 갱신되므로
 * 실제 동시성 문제가 재현되지 않습니다. 이 테스트는 문제 상황을 설명하는 용도입니다.
 * 
 * @Transactional로 자동 롤백되어 실제 DB에 영향 없음
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissingPetBoardConcurrencyTest {

    @Autowired
    private MissingPetBoardService missingPetBoardService;

    @Autowired
    private MissingPetBoardRepository boardRepository;

    @Autowired
    private MissingPetCommentRepository commentRepository;

    @Autowired
    private UsersRepository usersRepository;

    private Users testUser1;
    private Users testUser2;
    private Long boardId;

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 생성 (이메일 인증 완료)
        testUser1 = Users.builder()
                .id("test_user_1")
                .username("testuser1")
                .nickname("테스트유저1")
                .email("test1@test.com")
                .password("password")
                .emailVerified(true)
                .role(com.linkup.Petory.domain.user.entity.Role.USER)
                .status(com.linkup.Petory.domain.user.entity.UserStatus.ACTIVE)
                .isDeleted(false)
                .build();
        testUser1 = usersRepository.save(testUser1);

        testUser2 = Users.builder()
                .id("test_user_2")
                .username("testuser2")
                .nickname("테스트유저2")
                .email("test2@test.com")
                .password("password")
                .emailVerified(true)
                .role(com.linkup.Petory.domain.user.entity.Role.USER)
                .status(com.linkup.Petory.domain.user.entity.UserStatus.ACTIVE)
                .isDeleted(false)
                .build();
        testUser2 = usersRepository.save(testUser2);

        // 테스트용 게시글 생성
        MissingPetBoard board = MissingPetBoard.builder()
                .user(testUser1)
                .title("테스트 게시글")
                .content("테스트 내용")
                .petName("테스트펫")
                .species("강아지")
                .breed("골든리트리버")
                .gender(com.linkup.Petory.domain.board.entity.MissingPetGender.M)
                .age("3세")
                .color("골드")
                .lostDate(LocalDate.now())
                .lostLocation("테스트 위치")
                .latitude(new BigDecimal("37.5665"))
                .longitude(new BigDecimal("126.9780"))
                .status(MissingPetStatus.MISSING)
                .isDeleted(false)
                .build();
        board = boardRepository.save(board);
        boardId = board.getIdx();

        // 초기 댓글 3개 생성
        for (int i = 1; i <= 3; i++) {
            MissingPetComment comment = MissingPetComment.builder()
                    .board(board)
                    .user(testUser2)
                    .content("초기 댓글 " + i)
                    .address("테스트 주소 " + i)
                    .latitude(37.5665 + i * 0.001)
                    .longitude(126.9780 + i * 0.001)
                    .isDeleted(false)
                    .build();
            commentRepository.save(comment);
        }

        boardRepository.flush();
        commentRepository.flush();
    }

    @Test
    @DisplayName("게시글 삭제 시 댓글 Soft Delete 동시성 문제 설명")
    void testConcurrencyIssue_DeleteBoardWhileAddingComment() {
        // Given: 초기 상태 확인
        MissingPetBoard board = boardRepository.findById(boardId).orElseThrow();
        List<MissingPetComment> initialComments = commentRepository
                .findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
        assertThat(initialComments).hasSize(3);

        System.out.println("=== 동시성 문제 설명 테스트 ===");
        System.out.println("\n문제 상황:");
        System.out.println("deleteBoard() 메서드의 현재 구현:");
        System.out.println("```java");
        System.out.println("MissingPetBoard board = boardRepository.findById(id);");
        System.out.println("if (board.getComments() != null) {");
        System.out.println("    for (MissingPetComment c : board.getComments()) {");
        System.out.println("        c.setIsDeleted(true);");
        System.out.println("    }");
        System.out.println("}");
        System.out.println("```");
        System.out.println("\n문제점:");
        System.out.println("1. board.getComments()는 영속성 컨텍스트에 로드된 댓글만 포함");
        System.out.println("2. 트랜잭션 중간에 다른 사용자가 댓글을 추가하면 해당 댓글은 삭제되지 않음");
        System.out.println("3. orphanRemoval = true 설정과 충돌 가능성");

        // Step 1: 새 댓글 추가
        System.out.println("\nStep 1: 새 댓글 추가");
        MissingPetComment newComment = MissingPetComment.builder()
                .board(board)
                .user(testUser2)
                .content("동시에 추가된 댓글")
                .address("동시 추가 주소")
                .latitude(37.5670)
                .longitude(126.9790)
                .isDeleted(false)
                .build();
        commentRepository.saveAndFlush(newComment);
        System.out.println("  - 새 댓글 추가 완료 (총 4개 댓글)");

        // Step 2: 게시글 삭제 실행 전 상태 확인
        System.out.println("\nStep 2: 게시글 삭제 실행 전 상태 확인");
        MissingPetBoard boardBeforeDelete = boardRepository.findById(boardId).orElseThrow();
        List<MissingPetComment> commentsBeforeDelete = boardBeforeDelete.getComments();
        System.out.println("  - board.getComments() 반환값: "
                + (commentsBeforeDelete == null ? "null" : commentsBeforeDelete.size() + "개"));
        System.out.println("  - 이것이 deleteBoard() 내부에서 board.getComments()를 호출할 때 반환되는 값입니다");

        // Step 3: 게시글 삭제 실행
        System.out.println("\nStep 3: 게시글 삭제 실행");
        System.out.println("  - deleteBoard() 내부에서 board.getComments() 호출");
        System.out.println("  - 만약 board.getComments()가 null이거나 빈 리스트면 댓글이 삭제되지 않음");
        missingPetBoardService.deleteBoard(boardId);
        System.out.println("  - 삭제 완료");

        // Then: 결과 확인
        MissingPetBoard deletedBoard = boardRepository.findById(boardId).orElseThrow();
        assertThat(deletedBoard.getIsDeleted()).isTrue();

        // 댓글 상태 확인
        List<MissingPetComment> allComments = commentRepository.findAll();
        List<MissingPetComment> deletedComments = allComments.stream()
                .filter(comment -> comment.getBoard().getIdx().equals(boardId))
                .filter(comment -> Boolean.TRUE.equals(comment.getIsDeleted()))
                .toList();

        List<MissingPetComment> notDeletedComments = allComments.stream()
                .filter(comment -> comment.getBoard().getIdx().equals(boardId))
                .filter(comment -> !Boolean.TRUE.equals(comment.getIsDeleted()))
                .toList();

        System.out.println("\n=== 테스트 결과 ===");
        System.out.println("삭제된 댓글 수: " + deletedComments.size());
        System.out.println("삭제되지 않은 댓글 수: " + notDeletedComments.size());
        System.out.println("초기 댓글 수: 3개");
        System.out.println("추가된 댓글 수: 1개");
        System.out.println("총 댓글 수: 4개");

        // 문제 확인: 실제로 문제가 재현되었는지 확인
        if (notDeletedComments.size() > 0) {
            System.out.println("\n⚠️ 동시성 문제 재현 성공!");
            System.out.println("  - 삭제되지 않은 댓글이 " + notDeletedComments.size() + "개 있습니다.");
            System.out.println("  - 이것은 deleteBoard() 메서드의 문제를 보여줍니다.");
            System.out.println("\n문제 원인:");
            System.out.println("  - board.getComments()가 null이거나 빈 리스트를 반환함");
            System.out.println("  - 또는 board.getComments()가 영속성 컨텍스트에 로드된 댓글만 반환함");
            System.out.println("  - deleteBoard() 내부에서 board를 새로 조회하지만,");
            System.out.println("    board.getComments()를 호출할 때 LAZY 로딩이 제대로 작동하지 않을 수 있음");
        } else {
            System.out.println("\n✅ 모든 댓글이 정상적으로 삭제되었습니다.");
        }

        System.out.println("\n해결 방안:");
        System.out.println("  1. Repository를 통해 직접 댓글을 조회하여 삭제 처리");
        System.out.println("     List<MissingPetComment> comments = commentRepository");
        System.out.println("         .findByBoardAndIsDeletedFalse(board);");
        System.out.println("  2. 또는 @Query를 사용하여 한 번에 업데이트");
        System.out.println("     @Query(\"UPDATE MissingPetComment c SET c.isDeleted = true WHERE c.board = :board\")");

        // 문제가 재현되었는지 확인
        // 실제로는 모든 댓글이 삭제되어야 하지만, 문제가 재현되면 일부 댓글이 삭제되지 않음
        if (notDeletedComments.size() > 0) {
            System.out.println("\n✅ 동시성 문제가 성공적으로 재현되었습니다!");
            System.out.println("   이는 deleteBoard() 메서드의 문제를 확인하는 것입니다.");
        }

        // 테스트는 문제가 재현되는 것을 확인하는 것이 목적이므로
        // 삭제되지 않은 댓글이 있다면 문제가 재현된 것
        // (실제 프로덕션에서는 모든 댓글이 삭제되어야 함)
    }

    @Test
    @DisplayName("정상적인 삭제: 모든 댓글이 삭제되는지 확인")
    void testNormalDelete_AllCommentsDeleted() {
        // Given: 초기 댓글 3개
        MissingPetBoard board = boardRepository.findById(boardId).orElseThrow();
        List<MissingPetComment> initialComments = commentRepository
                .findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
        assertThat(initialComments).hasSize(3);

        // When: 게시글 삭제 (댓글 추가 없이)
        missingPetBoardService.deleteBoard(boardId);

        // Then: 모든 댓글이 삭제되었는지 확인
        List<MissingPetComment> notDeletedComments = commentRepository.findAll().stream()
                .filter(c -> c.getBoard().getIdx().equals(boardId))
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .toList();

        assertThat(notDeletedComments)
                .as("정상 삭제: 모든 댓글이 삭제되어야 합니다")
                .isEmpty();
    }
}
