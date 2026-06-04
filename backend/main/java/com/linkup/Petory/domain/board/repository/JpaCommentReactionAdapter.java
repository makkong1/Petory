package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.CommentReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * CommentReactionRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaCommentReactionAdapter implements CommentReactionRepository {

    private final SpringDataJpaCommentReactionRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public CommentReaction save(CommentReaction reaction) {
        return jpaRepository.save(reaction);
    }

    @SuppressWarnings("null")
    @Override
    public void delete(CommentReaction reaction) {
        jpaRepository.delete(reaction);
    }

    @Override
    public long countByCommentAndReactionType(Comment comment, ReactionType reactionType) {
        return jpaRepository.countByCommentAndReactionType(comment, reactionType);
    }

    @Override
    public Optional<CommentReaction> findByCommentAndUser(Comment comment, Users user) {
        return jpaRepository.findByCommentAndUser(comment, user);
    }

    @Override
    public List<Object[]> countByCommentsGroupByReactionType(List<Long> commentIds) {
        return jpaRepository.countByCommentsGroupByReactionType(commentIds);
    }

    @Override
    public int insertIgnore(Long commentId, Long userId, String reactionType) {
        return jpaRepository.insertIgnore(commentId, userId, reactionType);
    }
}
