package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.CommentReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    long countByCommentAndReactionType(Comment comment, ReactionType reactionType);

    Optional<CommentReaction> findByCommentAndUser(Comment comment, Users user);

    void deleteByComment(Comment comment);

    /**
     * 여러 댓글의 좋아요/싫어요 카운트를 한 번에 조회 (배치 조회)
     * 반환값: List<Object[]> [commentId, reactionType, count]
     */
    @Query("SELECT cr.comment.idx as commentId, cr.reactionType as reactionType, COUNT(cr) as count " +
           "FROM CommentReaction cr " +
           "WHERE cr.comment.idx IN :commentIds " +
           "GROUP BY cr.comment.idx, cr.reactionType")
    List<Object[]> countByCommentsGroupByReactionType(@Param("commentIds") List<Long> commentIds);
}

