package com.linkup.Petory.domain.board.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;

public interface BoardReactionRepository extends JpaRepository<BoardReaction, Long> {

    long countByBoardAndReactionType(Board board, ReactionType reactionType);

    Optional<BoardReaction> findByBoardAndUser(Board board, Users user);

    void deleteByBoard(Board board);
}
