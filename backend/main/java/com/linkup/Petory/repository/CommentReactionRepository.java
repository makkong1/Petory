package com.linkup.Petory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.entity.Comment;
import com.linkup.Petory.entity.CommentReaction;
import com.linkup.Petory.entity.ReactionType;
import com.linkup.Petory.entity.Users;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    long countByCommentAndReactionType(Comment comment, ReactionType reactionType);

    Optional<CommentReaction> findByCommentAndUser(Comment comment, Users user);

    void deleteByComment(Comment comment);
}

