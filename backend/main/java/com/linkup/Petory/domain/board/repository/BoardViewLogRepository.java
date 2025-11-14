package com.linkup.Petory.domain.board.repository;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardViewLog;
import com.linkup.Petory.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardViewLogRepository extends JpaRepository<BoardViewLog, Long> {

    boolean existsByBoardAndUser(Board board, Users user);
}

