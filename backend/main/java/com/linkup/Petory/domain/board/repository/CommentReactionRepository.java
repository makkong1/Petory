package com.linkup.Petory.domain.board.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.CommentReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    long countByCommentAndReactionType(Comment comment, ReactionType reactionType);

    Optional<CommentReaction> findByCommentAndUser(Comment comment, Users user);

    void deleteByComment(Comment comment);
}
