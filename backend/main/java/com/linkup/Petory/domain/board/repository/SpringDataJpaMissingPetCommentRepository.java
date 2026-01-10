package com.linkup.Petory.domain.board.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaMissingPetCommentRepository extends JpaRepository<MissingPetComment, Long> {

    List<MissingPetComment> findByBoardOrderByCreatedAtAsc(MissingPetBoard board);

    // soft-deleted 제외 - 작성자도 활성 상태여야 함
    @Query("SELECT mc FROM MissingPetComment mc JOIN FETCH mc.user u WHERE mc.board = :board AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY mc.createdAt ASC")
    List<MissingPetComment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(@Param("board") MissingPetBoard board);

    // 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순) - JOIN FETCH로 N+1 문제 해결 - 작성자도 활성 상태여야 함
    @Query("SELECT mc FROM MissingPetComment mc JOIN FETCH mc.board b JOIN FETCH b.user bu JOIN FETCH mc.user u WHERE mc.user = :user AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY mc.createdAt DESC")
    List<MissingPetComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    // 게시글별 댓글 수 배치 조회 (N+1 문제 해결)
    @Query("SELECT mc.board.idx, COUNT(mc) FROM MissingPetComment mc " +
           "JOIN mc.user u " +
           "WHERE mc.board.idx IN :boardIds " +
           "AND mc.isDeleted = false " +
           "AND u.isDeleted = false " +
           "AND u.status = 'ACTIVE' " +
           "GROUP BY mc.board.idx")
    List<Object[]> countCommentsByBoardIds(@Param("boardIds") List<Long> boardIds);

    // 페이징 지원 - 게시글별 댓글 조회 (JOIN FETCH와 페이징 호환을 위해 COUNT 쿼리 별도 지정)
    @Query(value = "SELECT mc FROM MissingPetComment mc JOIN FETCH mc.user u WHERE mc.board.idx = :boardId AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY mc.createdAt ASC",
           countQuery = "SELECT COUNT(mc) FROM MissingPetComment mc JOIN mc.user u WHERE mc.board.idx = :boardId AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Page<MissingPetComment> findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(@Param("boardId") Long boardId, Pageable pageable);
}

