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
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    @RepositoryMethod("댓글 반응: 반응 타입별 카운트")
    long countByCommentAndReactionType(Comment comment, ReactionType reactionType);

    @RepositoryMethod("댓글 반응: 사용자별 조회")
    Optional<CommentReaction> findByCommentAndUser(Comment comment, Users user);

    @RepositoryMethod("댓글 반응: 댓글별 삭제")
    void deleteByComment(Comment comment);

    @RepositoryMethod("댓글 반응: 배치 카운트 조회")
    @Query("SELECT cr.comment.idx as commentId, cr.reactionType as reactionType, COUNT(cr) as count " +
           "FROM CommentReaction cr " +
           "WHERE cr.comment.idx IN :commentIds " +
           "GROUP BY cr.comment.idx, cr.reactionType")
    List<Object[]> countByCommentsGroupByReactionType(@Param("commentIds") List<Long> commentIds);
}

