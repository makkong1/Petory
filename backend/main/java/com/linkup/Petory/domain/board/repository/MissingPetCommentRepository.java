package com.linkup.Petory.domain.board.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.user.entity.Users;

public interface MissingPetCommentRepository extends JpaRepository<MissingPetComment, Long> {

    List<MissingPetComment> findByBoardOrderByCreatedAtAsc(MissingPetBoard board);

    // soft-deleted 제외
    List<MissingPetComment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(MissingPetBoard board);

    // 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순) - JOIN FETCH로 N+1 문제 해결
    @Query("SELECT mc FROM MissingPetComment mc JOIN FETCH mc.board WHERE mc.user = :user AND mc.isDeleted = false ORDER BY mc.createdAt DESC")
    List<MissingPetComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);
}
