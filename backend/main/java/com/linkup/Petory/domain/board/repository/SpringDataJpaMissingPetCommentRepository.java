package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaMissingPetCommentRepository extends JpaRepository<MissingPetComment, Long> {

    @RepositoryMethod("실종 댓글: 게시글별 목록 조회")
    List<MissingPetComment> findByBoardOrderByCreatedAtAsc(MissingPetBoard board);

    @RepositoryMethod("실종 댓글: 게시글별 목록 (삭제 제외)")
    @Query("SELECT mc FROM MissingPetComment mc JOIN FETCH mc.user u WHERE mc.board = :board AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY mc.createdAt ASC")
    List<MissingPetComment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(@Param("board") MissingPetBoard board);

    @RepositoryMethod("실종 댓글: 게시글별 댓글 수")
    @Query("SELECT COUNT(mc) FROM MissingPetComment mc JOIN mc.user u " +
           "WHERE mc.board = :board AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    long countByBoardAndIsDeletedFalse(@Param("board") MissingPetBoard board);

    @RepositoryMethod("실종 댓글: 사용자별 목록 조회")
    @Query("SELECT mc FROM MissingPetComment mc JOIN FETCH mc.board b JOIN FETCH b.user bu JOIN FETCH mc.user u WHERE mc.user = :user AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY mc.createdAt DESC")
    List<MissingPetComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    @RepositoryMethod("실종 댓글: 게시글별 댓글 수 배치 조회")
    @Query("SELECT mc.board.idx, COUNT(mc) FROM MissingPetComment mc " +
           "JOIN mc.user u " +
           "WHERE mc.board.idx IN :boardIds " +
           "AND mc.isDeleted = false " +
           "AND u.isDeleted = false " +
           "AND u.status = 'ACTIVE' " +
           "GROUP BY mc.board.idx")
    List<Object[]> countCommentsByBoardIds(@Param("boardIds") List<Long> boardIds);

    @RepositoryMethod("실종 댓글: 게시글별 페이징 조회")
    @Query(value = "SELECT mc FROM MissingPetComment mc JOIN FETCH mc.user u WHERE mc.board.idx = :boardId AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY mc.createdAt ASC",
           countQuery = "SELECT COUNT(mc) FROM MissingPetComment mc JOIN mc.user u WHERE mc.board.idx = :boardId AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Page<MissingPetComment> findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(@Param("boardId") Long boardId, Pageable pageable);

    @RepositoryMethod("실종 댓글: 게시글별 일괄 소프트 삭제")
    @Modifying(clearAutomatically = true)
    @Query("UPDATE MissingPetComment mc SET mc.isDeleted = true, mc.deletedAt = :deletedAt WHERE mc.board.idx = :boardIdx AND mc.isDeleted = false")
    int softDeleteAllByBoardIdx(@Param("boardIdx") Long boardIdx, @Param("deletedAt") LocalDateTime deletedAt);
}

