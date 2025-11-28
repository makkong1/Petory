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

        // 전체 게시글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();

        // 전체 게시글 조회 (삭제되지 않은 것만, 최신순) - 페이징 - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        Page<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

        // 카테고리별 게시글 조회 (최신순)
        List<Board> findByCategoryOrderByCreatedAtDesc(String category);

        // 카테고리별 삭제되지 않은 게시글 조회 (최신순) - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.category = :category AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        List<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(@Param("category") String category);

        // 카테고리별 삭제되지 않은 게시글 조회 (최신순) - 페이징 - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.category = :category AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        Page<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(@Param("category") String category, Pageable pageable);

        // 사용자별 게시글 조회 (최신순)
        List<Board> findByUserOrderByCreatedAtDesc(Users user);

        // 사용자별 삭제되지 않은 게시글 조회 (최신순) - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.user = :user AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        List<Board> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

        // 제목으로 검색 - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.title LIKE %:title% AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        List<Board> findByTitleContainingAndIsDeletedFalseOrderByCreatedAtDesc(@Param("title") String title);

        // 제목으로 검색 (페이징) - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.title LIKE %:title% AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        Page<Board> findByTitleContainingAndIsDeletedFalseOrderByCreatedAtDesc(@Param("title") String title, Pageable pageable);

        // 내용으로 검색 - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.content LIKE %:content% AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        List<Board> findByContentContainingAndIsDeletedFalseOrderByCreatedAtDesc(@Param("content") String content);

        // 내용으로 검색 (페이징) - 작성자도 활성 상태여야 함
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.content LIKE %:content% AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
        Page<Board> findByContentContainingAndIsDeletedFalseOrderByCreatedAtDesc(@Param("content") String content, Pageable pageable);

        // FULLTEXT 인덱스 사용 쿼리 (제목+내용) - 페이징 - 작성자도 활성 상태여야 함
        @Query(value = "SELECT b.*, " + "MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) as relevance "
                        + "FROM board b " 
                        + "INNER JOIN users u ON b.user_idx = u.idx "
                        + "WHERE b.is_deleted = false "
                        + "AND u.is_deleted = false "
                        + "AND u.status = 'ACTIVE' "
                        + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) "
                        + "ORDER BY relevance DESC, b.created_at DESC", 
                        countQuery = "SELECT COUNT(*) FROM board b "
                        + "INNER JOIN users u ON b.user_idx = u.idx "
                        + "WHERE b.is_deleted = false "
                        + "AND u.is_deleted = false "
                        + "AND u.status = 'ACTIVE' "
                        + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE)", nativeQuery = true)
        Page<Board> searchByKeywordWithPaging(@Param("kw") String keyword, Pageable pageable);

        // 카테고리 + 기간별 조회
        List<Board> findByCategoryAndCreatedAtBetween(String category, LocalDateTime start, LocalDateTime end);

        // 통계용
        long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

        // 관리자용: 작성자 상태 체크 없이 조회 (삭제된 사용자 콘텐츠도 포함)
        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false ORDER BY b.createdAt DESC")
        Page<Board> findAllByIsDeletedFalseForAdmin(Pageable pageable);

        @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.category = :category AND b.isDeleted = false ORDER BY b.createdAt DESC")
        Page<Board> findByCategoryAndIsDeletedFalseForAdmin(@Param("category") String category, Pageable pageable);
}
