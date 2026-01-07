package com.linkup.Petory.domain.board.repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.CommentReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;

import lombok.RequiredArgsConstructor;

/**
 * CommentReactionRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaCommentReactionAdapter implements CommentReactionRepository {

    private final SpringDataJpaCommentReactionRepository jpaRepository;

    @Override
    public CommentReaction save(CommentReaction reaction) {
        return jpaRepository.save(reaction);
    }

    @Override
    public Optional<CommentReaction> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(CommentReaction reaction) {
        jpaRepository.delete(reaction);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
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
    public void deleteByComment(Comment comment) {
        jpaRepository.deleteByComment(comment);
    }
}

