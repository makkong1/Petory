package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaMissingPetBoardAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaMissingPetBoardRepository extends JpaRepository<MissingPetBoard, Long> {

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findAllByOrderByCreatedAtDesc();

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.status = :status AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(@Param("status") MissingPetStatus status);

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.idx = :id AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Optional<MissingPetBoard> findByIdWithUser(@Param("id") Long id);

    // 댓글 포함 조회 (N+1 문제 해결) - 전체 조회
    @Query("SELECT DISTINCT b FROM MissingPetBoard b " +
            "JOIN FETCH b.user u " +
            "LEFT JOIN FETCH b.comments c " +
            "LEFT JOIN FETCH c.user cu " +
            "WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
            "AND (c IS NULL OR (c.isDeleted = false AND cu.isDeleted = false AND cu.status = 'ACTIVE')) " +
            "ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findAllWithCommentsByOrderByCreatedAtDesc();

    // 댓글 포함 조회 (N+1 문제 해결) - 상태별 조회
    @Query("SELECT DISTINCT b FROM MissingPetBoard b " +
            "JOIN FETCH b.user u " +
            "LEFT JOIN FETCH b.comments c " +
            "LEFT JOIN FETCH c.user cu " +
            "WHERE b.status = :status AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
            "AND (c IS NULL OR (c.isDeleted = false AND cu.isDeleted = false AND cu.status = 'ACTIVE')) " +
            "ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findByStatusWithCommentsOrderByCreatedAtDesc(@Param("status") MissingPetStatus status);

    // 댓글 포함 단건 조회 (N+1 문제 해결)
    @Query("SELECT DISTINCT b FROM MissingPetBoard b " +
            "JOIN FETCH b.user u " +
            "LEFT JOIN FETCH b.comments c " +
            "LEFT JOIN FETCH c.user cu " +
            "WHERE b.idx = :id AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
            "AND (c IS NULL OR (c.isDeleted = false AND cu.isDeleted = false AND cu.status = 'ACTIVE'))")
    Optional<MissingPetBoard> findByIdWithComments(@Param("id") Long id);

    // 사용자별 게시글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.user = :user AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    // 페이징 지원 - 전체 조회 (JOIN FETCH와 페이징 호환을 위해 COUNT 쿼리 별도 지정)
    @Query(value = "SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM MissingPetBoard b JOIN b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Page<MissingPetBoard> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 페이징 지원 - 상태별 조회 (JOIN FETCH와 페이징 호환을 위해 COUNT 쿼리 별도 지정)
    @Query(value = "SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.status = :status AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM MissingPetBoard b JOIN b.user u WHERE b.status = :status AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Page<MissingPetBoard> findByStatusOrderByCreatedAtDesc(@Param("status") MissingPetStatus status, Pageable pageable);
}

