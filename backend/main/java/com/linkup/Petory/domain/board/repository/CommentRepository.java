package com.linkup.Petory.domain.board.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.user.entity.Users;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByBoardOrderByCreatedAtAsc(Board board);

    // 삭제되지 않은 댓글만 조회
    List<Comment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(Board board);

    // 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순)
    List<Comment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);
}
