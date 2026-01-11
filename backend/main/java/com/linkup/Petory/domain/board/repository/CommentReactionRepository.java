package com.linkup.Petory.domain.board.repository;

import java.util.Optional;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.CommentReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;

/**
 * CommentReaction 도메인 Repository 인터페이스입니다.
 */
public interface CommentReactionRepository {

    CommentReaction save(CommentReaction reaction);

    Optional<CommentReaction> findById(Long id);

    void delete(CommentReaction reaction);

    void deleteById(Long id);

    long countByCommentAndReactionType(Comment comment, ReactionType reactionType);

    Optional<CommentReaction> findByCommentAndUser(Comment comment, Users user);

    void deleteByComment(Comment comment);
}
