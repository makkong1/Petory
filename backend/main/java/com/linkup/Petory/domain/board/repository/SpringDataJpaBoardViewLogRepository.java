package com.linkup.Petory.domain.board.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardViewLog;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaBoardViewLogRepository extends JpaRepository<BoardViewLog, Long> {

    boolean existsByBoardAndUser(Board board, Users user);

    /**
     * 여러 게시글의 조회수 카운트를 한 번에 조회 (배치 조회)
     * 반환값: List<Object[]> [boardId, count]
     */
    @Query("SELECT bvl.board.idx as boardId, COUNT(bvl) as count " +
           "FROM BoardViewLog bvl " +
           "WHERE bvl.board.idx IN :boardIds " +
           "GROUP BY bvl.board.idx")
    List<Object[]> countByBoards(@Param("boardIds") List<Long> boardIds);
}

