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

    void delete(BoardReaction reaction);

    long countByBoardAndReactionType(Board board, ReactionType reactionType);

    Optional<BoardReaction> findByBoardAndUser(Board board, Users user);

    /**
     * 여러 게시글의 좋아요/싫어요 카운트를 한 번에 조회 (배치 조회)
     * 반환값: List<Object[]> [boardId, reactionType, count]
     */
    List<Object[]> countByBoardsGroupByReactionType(List<Long> boardIds);

    /**
     * [리팩토링] 여러 게시글의 특정 반응 타입(LIKE 등) 카운트만 배치 조회
     * 반환값: List<Object[]> [boardId, count]
     */
    List<Object[]> countByBoardsAndReactionType(List<Long> boardIds, ReactionType reactionType);
}
