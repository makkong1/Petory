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
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaCommentAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaCommentRepository extends JpaRepository<Comment, Long> {

    @RepositoryMethod("댓글: 게시글별 목록 조회")
    List<Comment> findByBoardOrderByCreatedAtAsc(Board board);

    @RepositoryMethod("댓글: 게시글별 목록 조회 (삭제 제외)")
    @Query("SELECT c FROM Comment c JOIN FETCH c.user u WHERE c.board = :board AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY c.createdAt ASC")
    List<Comment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(@Param("board") Board board);

    @RepositoryMethod("댓글: 사용자별 목록 조회")
    @Query("SELECT c FROM Comment c JOIN FETCH c.board b JOIN FETCH b.user bu JOIN FETCH c.user u WHERE c.user = :user AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY c.createdAt DESC")
    List<Comment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    @RepositoryMethod("댓글: 게시글별 댓글 수 배치 조회")
    @Query("SELECT c.board.idx as boardId, COUNT(c) as count " +
           "FROM Comment c JOIN c.user u " +
           "WHERE c.board.idx IN :boardIds AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
           "GROUP BY c.board.idx")
    List<Object[]> countByBoardsAndIsDeletedFalse(@Param("boardIds") List<Long> boardIds);

    @RepositoryMethod("댓글: 관리자용 게시글별 목록")
    @Query("SELECT c FROM Comment c JOIN FETCH c.user u WHERE c.board = :board AND c.isDeleted = false ORDER BY c.createdAt ASC")
    List<Comment> findByBoardAndIsDeletedFalseForAdmin(@Param("board") Board board);

    @RepositoryMethod("댓글: 게시글별 페이징 조회")
    @Query(value = "SELECT c FROM Comment c JOIN FETCH c.user u WHERE c.board.idx = :boardId AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY c.createdAt ASC",
           countQuery = "SELECT COUNT(c) FROM Comment c JOIN c.user u WHERE c.board.idx = :boardId AND c.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Page<Comment> findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(@Param("boardId") Long boardId, Pageable pageable);
}

