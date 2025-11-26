package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.user.entity.Users;

@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {

        // 전체 게시글 조회 (최신순)
        List<Board> findAllByOrderByCreatedAtDesc();

        // 전체 게시글 조회 (삭제되지 않은 것만, 최신순)
        List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();

        // 전체 게시글 조회 (삭제되지 않은 것만, 최신순) - 페이징
        Page<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

        // 카테고리별 게시글 조회 (최신순)
        List<Board> findByCategoryOrderByCreatedAtDesc(String category);

        // 카테고리별 삭제되지 않은 게시글 조회 (최신순)
        List<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category);

        // 카테고리별 삭제되지 않은 게시글 조회 (최신순) - 페이징
        Page<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category, Pageable pageable);

        // 사용자별 게시글 조회 (최신순)
        List<Board> findByUserOrderByCreatedAtDesc(Users user);

        // 사용자별 삭제되지 않은 게시글 조회 (최신순)
        List<Board> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);

        // FULLTEXT 인덱스 사용 쿼리
        @Query(value = "SELECT b.*, " + "MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) as relevance "
                        + "FROM board b " + "WHERE b.is_deleted = false "
                        + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) "
                        + "ORDER BY relevance DESC, b.created_at DESC", nativeQuery = true)
        List<Board> searchByKeyword(@Param("kw") String keyword);

        // 카테고리 + 기간별 조회
        List<Board> findByCategoryAndCreatedAtBetween(String category, LocalDateTime start, LocalDateTime end);

        // 통계용
        long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
