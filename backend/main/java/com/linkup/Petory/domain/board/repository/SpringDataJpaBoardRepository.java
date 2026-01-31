package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaBoardAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL, Specification 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaBoardRepository extends JpaRepository<Board, Long>, JpaSpecificationExecutor<Board> {

    // 전체 게시글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    // 전체 게시글 조회 (삭제되지 않은 것만, 최신순) - 페이징 - 작성자도 활성 상태여야 함
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    Page<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    // 카테고리별 삭제되지 않은 게시글 조회 (최신순) - 작성자도 활성 상태여야 함
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.category = :category AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(@Param("category") String category);

    // 카테고리별 삭제되지 않은 게시글 조회 (최신순) - 페이징 - 작성자도 활성 상태여야 함
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.category = :category AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    Page<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(@Param("category") String category, Pageable pageable);

    // 사용자별 삭제되지 않은 게시글 조회 (최신순) - 작성자도 활성 상태여야 함
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.user = :user AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<Board> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    // 작성자 닉네임으로 검색 (페이징) - JOIN 쿼리로 최적화
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE u.nickname LIKE %:nickname% AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    Page<Board> searchByNicknameWithPaging(@Param("nickname") String nickname, Pageable pageable);

    // FULLTEXT 인덱스 사용 쿼리 (제목+내용) - 페이징 - 작성자도 활성 상태여야 함
    @Query(value = "SELECT b.*, MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) AS relevance "
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

    // 관리자용: 작성자 상태 체크 없이 조회 (삭제된 사용자 콘텐츠도 포함) - 전체 조회
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false ORDER BY b.createdAt DESC")
    List<Board> findAllByIsDeletedFalseForAdmin();

    // 관리자용: 전체 조회 (삭제 포함)
    @Query("SELECT b FROM Board b JOIN FETCH b.user u ORDER BY b.createdAt DESC")
    List<Board> findAllForAdmin();
}

