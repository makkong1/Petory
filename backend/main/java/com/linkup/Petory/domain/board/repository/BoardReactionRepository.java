package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;

/**
 * BoardReaction 도메인 Repository 인터페이스입니다.
 */
public interface BoardReactionRepository {

    BoardReaction save(BoardReaction reaction);

    Optional<BoardReaction> findById(Long id);

    void delete(BoardReaction reaction);

    void deleteById(Long id);

    long countByBoardAndReactionType(Board board, ReactionType reactionType);

    Optional<BoardReaction> findByBoardAndUser(Board board, Users user);

    void deleteByBoard(Board board);

    /**
     * 여러 게시글의 좋아요/싫어요 카운트를 한 번에 조회 (배치 조회)
     * 반환값: List<Object[]> [boardId, reactionType, count]
     */
    List<Object[]> countByBoardsGroupByReactionType(List<Long> boardIds);

    /**
     * 단일 게시글의 좋아요/싫어요 카운트를 한 번에 조회
     * 반환값: List<Object[]> [boardId, reactionType, count]
     */
    List<Object[]> countByBoardGroupByReactionType(Long boardId);
}
