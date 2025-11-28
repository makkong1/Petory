package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.user.entity.Users;

public interface MissingPetBoardRepository extends JpaRepository<MissingPetBoard, Long> {

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findAllByOrderByCreatedAtDesc();

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.status = :status AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(@Param("status") MissingPetStatus status);

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.idx = :id AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Optional<MissingPetBoard> findByIdWithUser(@Param("id") Long id);

    // 사용자별 게시글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.user = :user AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);
}
