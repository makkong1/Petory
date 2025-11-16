package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;

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

        // 카테고리별 게시글 조회 (최신순)
        List<Board> findByCategoryOrderByCreatedAtDesc(String category);

        // 카테고리별 삭제되지 않은 게시글 조회 (최신순)
        List<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category);

        // 사용자별 게시글 조회 (최신순)
        List<Board> findByUserOrderByCreatedAtDesc(Users user);

        // 사용자별 삭제되지 않은 게시글 조회 (최신순)
        List<Board> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);

        // 제목/내용 키워드 + 삭제되지 않은 게시글 검색 (content은 CLOB이므로 함수형 ignoreCase 대신 DB 기본
        // collation 활용)
        @Query("select b from Board b where b.isDeleted = false and (lower(b.title) like lower(concat('%', :kw, '%')) or b.content like concat('%', :kw, '%')) order by b.createdAt desc")
        List<Board> searchByKeyword(@Param("kw") String keyword);

        // 카테고리 + 기간별 조회
        List<Board> findByCategoryAndCreatedAtBetween(String category, LocalDateTime start, LocalDateTime end);
}
