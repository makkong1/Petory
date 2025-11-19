package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;

public interface BoardReactionRepository extends JpaRepository<BoardReaction, Long> {

    long countByBoardAndReactionType(Board board, ReactionType reactionType);

    Optional<BoardReaction> findByBoardAndUser(Board board, Users user);

    void deleteByBoard(Board board);

    /**
     * 여러 게시글의 좋아요/싫어요 카운트를 한 번에 조회 (배치 조회)
     * 반환값: Map<BoardId, Map<ReactionType, Count>>
     */
    @Query("SELECT br.board.idx as boardId, br.reactionType as reactionType, COUNT(br) as count " +
           "FROM BoardReaction br " +
           "WHERE br.board.idx IN :boardIds " +
           "GROUP BY br.board.idx, br.reactionType")
    List<Object[]> countByBoardsGroupByReactionType(@Param("boardIds") List<Long> boardIds);
}
