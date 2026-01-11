package com.linkup.Petory.domain.board.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaCommentAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaCommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByBoardOrderByCreatedAtAsc(Board board);

    // 삭제되지 않은 댓글만 조회 - 작성자도 활성 상태여야 함
    @Query("SELECT c FROM Comment c JOIN FETCH c.user u WHERE c.board = :board AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY c.createdAt ASC")
    List<Comment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(@Param("board") Board board);

    // 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순) - JOIN FETCH로 N+1 문제 해결 - 작성자도 활성 상태여야 함
    @Query("SELECT c FROM Comment c JOIN FETCH c.board b JOIN FETCH b.user bu JOIN FETCH c.user u WHERE c.user = :user AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY c.createdAt DESC")
    List<Comment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    /**
     * 여러 게시글의 댓글 카운트를 한 번에 조회 (배치 조회) - 작성자도 활성 상태여야 함
     * 반환값: List<Object[]> [boardId, count]
     */
    @Query("SELECT c.board.idx as boardId, COUNT(c) as count " +
           "FROM Comment c JOIN c.user u " +
           "WHERE c.board.idx IN :boardIds AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
           "GROUP BY c.board.idx")
    List<Object[]> countByBoardsAndIsDeletedFalse(@Param("boardIds") List<Long> boardIds);

    // 관리자용: 작성자 상태 체크 없이 조회 (삭제된 사용자 댓글도 포함)
    @Query("SELECT c FROM Comment c JOIN FETCH c.user u WHERE c.board = :board AND c.isDeleted = false ORDER BY c.createdAt ASC")
    List<Comment> findByBoardAndIsDeletedFalseForAdmin(@Param("board") Board board);

    // 페이징 지원 - 게시글별 댓글 조회 (JOIN FETCH와 페이징 호환을 위해 COUNT 쿼리 별도 지정)
    @Query(value = "SELECT c FROM Comment c JOIN FETCH c.user u WHERE c.board.idx = :boardId AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY c.createdAt ASC",
           countQuery = "SELECT COUNT(c) FROM Comment c JOIN c.user u WHERE c.board.idx = :boardId AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Page<Comment> findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(@Param("boardId") Long boardId, Pageable pageable);
}

