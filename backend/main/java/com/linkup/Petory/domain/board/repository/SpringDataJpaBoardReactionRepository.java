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
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaBoardReactionRepository extends JpaRepository<BoardReaction, Long> {

    @RepositoryMethod("게시글 반응: 좋아요/싫어요 카운트")
    long countByBoardAndReactionType(Board board, ReactionType reactionType);

    @RepositoryMethod("게시글 반응: 사용자별 조회")
    Optional<BoardReaction> findByBoardAndUser(Board board, Users user);

    @RepositoryMethod("게시글 반응: 게시글별 삭제")
    void deleteByBoard(Board board);

    @RepositoryMethod("게시글 반응: 배치 카운트 조회")
    @Query("SELECT br.board.idx as boardId, br.reactionType as reactionType, COUNT(br) as count " +
           "FROM BoardReaction br " +
           "WHERE br.board.idx IN :boardIds " +
           "GROUP BY br.board.idx, br.reactionType")
    List<Object[]> countByBoardsGroupByReactionType(@Param("boardIds") List<Long> boardIds);

    @RepositoryMethod("게시글 반응: 단건 카운트 조회")
    @Query("SELECT br.board.idx as boardId, br.reactionType as reactionType, COUNT(br) as count " +
           "FROM BoardReaction br " +
           "WHERE br.board.idx = :boardId " +
           "GROUP BY br.board.idx, br.reactionType")
    List<Object[]> countByBoardGroupByReactionType(@Param("boardId") Long boardId);
}

