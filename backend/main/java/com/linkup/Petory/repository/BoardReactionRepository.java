package com.linkup.Petory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.entity.Board;
import com.linkup.Petory.entity.BoardReaction;
import com.linkup.Petory.entity.ReactionType;
import com.linkup.Petory.entity.Users;

public interface BoardReactionRepository extends JpaRepository<BoardReaction, Long> {

    long countByBoardAndReactionType(Board board, ReactionType reactionType);

    Optional<BoardReaction> findByBoardAndUser(Board board, Users user);

    void deleteByBoard(Board board);
}

