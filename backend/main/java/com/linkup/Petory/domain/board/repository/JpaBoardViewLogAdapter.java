package com.linkup.Petory.domain.board.repository;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardViewLog;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * BoardViewLogRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaBoardViewLogAdapter implements BoardViewLogRepository {

    private final SpringDataJpaBoardViewLogRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public BoardViewLog save(BoardViewLog viewLog) {
        return jpaRepository.save(viewLog);
    }

    @Override
    public boolean existsByBoardAndUser(Board board, Users user) {
        return jpaRepository.existsByBoardAndUser(board, user);
    }

    @Override
    public List<Object[]> countByBoards(List<Long> boardIds) {
        return jpaRepository.countByBoards(boardIds);
    }
}
