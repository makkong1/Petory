package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardViewLog;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * BoardViewLog 도메인 Repository 인터페이스입니다.
 */
public interface BoardViewLogRepository {

    BoardViewLog save(BoardViewLog viewLog);

    Optional<BoardViewLog> findById(Long id);

    void delete(BoardViewLog viewLog);

    void deleteById(Long id);

    boolean existsByBoardAndUser(Board board, Users user);

    /**
     * 여러 게시글의 조회수 카운트를 한 번에 조회 (배치 조회)
     * 반환값: List<Object[]> [boardId, count]
     */
    List<Object[]> countByBoards(List<Long> boardIds);
}

