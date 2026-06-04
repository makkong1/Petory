package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * BoardReactionRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaBoardReactionAdapter implements BoardReactionRepository {

    private final SpringDataJpaBoardReactionRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public BoardReaction save(BoardReaction reaction) {
        return jpaRepository.save(reaction);
    }

    @SuppressWarnings("null")
    @Override
    public void delete(BoardReaction reaction) {
        jpaRepository.delete(reaction);
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
    public List<Object[]> countByBoardsGroupByReactionType(List<Long> boardIds) {
        return jpaRepository.countByBoardsGroupByReactionType(boardIds);
    }

    @Override
    // [리팩토링] BoardPopularityService 배치 집계용 LIKE 전용 조회
    public List<Object[]> countByBoardsAndReactionType(List<Long> boardIds, ReactionType reactionType) {
        return jpaRepository.countByBoardsAndReactionType(boardIds, reactionType);
    }

    @Override
    public int insertIgnore(Long boardId, Long userId, String reactionType) {
        return jpaRepository.insertIgnore(boardId, userId, reactionType);
    }
}
