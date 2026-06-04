package com.linkup.Petory.domain.board.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardViewLog;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaBoardViewLogRepository extends JpaRepository<BoardViewLog, Long> {

    @RepositoryMethod("조회수 로그: 조회 여부 확인")
    boolean existsByBoardAndUser(Board board, Users user);

    @RepositoryMethod("조회수 로그: 배치 카운트 조회")
    @Query("SELECT bvl.board.idx as boardId, COUNT(bvl) as count " +
           "FROM BoardViewLog bvl " +
           "WHERE bvl.board.idx IN :boardIds " +
           "GROUP BY bvl.board.idx")
    List<Object[]> countByBoards(@Param("boardIds") List<Long> boardIds);

    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO board_view_log (board_id, user_id, viewed_at) VALUES (:boardId, :userId, NOW())", nativeQuery = true)
    int insertIgnore(@Param("boardId") Long boardId, @Param("userId") Long userId);
}

