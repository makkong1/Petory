package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;

import lombok.RequiredArgsConstructor;

/**
 * BoardReactionRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaBoardReactionAdapter implements BoardReactionRepository {

    private final SpringDataJpaBoardReactionRepository jpaRepository;

    @Override
    public BoardReaction save(BoardReaction reaction) {
        return jpaRepository.save(reaction);
    }

    @Override
    public Optional<BoardReaction> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(BoardReaction reaction) {
        jpaRepository.delete(reaction);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public long countByBoardAndReactionType(Board board, ReactionType reactionType) {
        return jpaRepository.countByBoardAndReactionType(board, reactionType);
    }

    @Override
    public Optional<BoardReaction> findByBoardAndUser(Board board, Users user) {
        return jpaRepository.findByBoardAndUser(board, user);
    }

    @Override
    public void deleteByBoard(Board board) {
        jpaRepository.deleteByBoard(board);
    }

    @Override
    public List<Object[]> countByBoardsGroupByReactionType(List<Long> boardIds) {
        return jpaRepository.countByBoardsGroupByReactionType(boardIds);
    }

    @Override
    public List<Object[]> countByBoardGroupByReactionType(Long boardId) {
        return jpaRepository.countByBoardGroupByReactionType(boardId);
    }
}

