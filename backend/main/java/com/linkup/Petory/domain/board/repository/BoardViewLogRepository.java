package com.linkup.Petory.domain.board.repository;

import java.util.List;

import com.linkup.Petory.domain.board.entity.BoardViewLog;

/**
 * BoardViewLog 도메인 Repository 인터페이스입니다.
 */
public interface BoardViewLogRepository {

    BoardViewLog save(BoardViewLog viewLog);

    int insertIgnore(Long boardId, Long userId);

    /**
     * 여러 게시글의 조회수 카운트를 한 번에 조회 (배치 조회) 반환값: List<Object[]> [boardId, count]
     */
    List<Object[]> countByBoards(List<Long> boardIds);
}
