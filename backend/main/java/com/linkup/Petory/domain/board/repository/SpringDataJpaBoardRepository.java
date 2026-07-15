package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.dto.BoardListItemDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 *
 * 이 인터페이스는 JpaBoardAdapter 내부에서만 사용되며, 도메인 레이어에서는 직접 사용하지 않습니다.
 *
 * JPA 특화 기능(쿼리 메서드, JPQL, Specification 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaBoardRepository extends JpaRepository<Board, Long>, JpaSpecificationExecutor<Board> {

    @RepositoryMethod("게시글: 전체 목록 조회")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    @RepositoryMethod("게시글: 전체 목록 페이징")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    Page<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    @RepositoryMethod("게시글: 카테고리별 목록 조회")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.category = :category AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(@Param("category") String category);

    @RepositoryMethod("게시글: 카테고리별 목록 페이징")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.category = :category AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    Page<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(@Param("category") String category,
            Pageable pageable);

    @RepositoryMethod("게시글: 사용자별 목록 조회")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.user = :user AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<Board> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    @RepositoryMethod("게시글: 작성자 닉네임 검색 페이징")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE u.nickname LIKE :nickname% AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    Page<Board> searchByNicknameWithPaging(@Param("nickname") String nickname, Pageable pageable);

    @RepositoryMethod("게시글: FULLTEXT 키워드 검색 페이징")
    @Query(value = "SELECT b.*, MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) AS relevance "
            + "FROM board b "
            + "INNER JOIN users u ON b.user_idx = u.idx "
            + "WHERE b.is_deleted = false "
            + "AND u.is_deleted = false "
            + "AND u.status = 'ACTIVE' "
            + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) "
            + "ORDER BY relevance DESC, b.created_at DESC", countQuery = "SELECT COUNT(*) FROM board b "
            + "INNER JOIN users u ON b.user_idx = u.idx "
            + "WHERE b.is_deleted = false "
            + "AND u.is_deleted = false "
            + "AND u.status = 'ACTIVE' "
            + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE)", nativeQuery = true)
    Page<Board> searchByKeywordWithPaging(@Param("kw") String keyword, Pageable pageable);

    // ── [오버페칭 제거] 목록 projection ──
    // 기존 목록 쿼리는 JOIN FETCH b.user 로 작성자(Users) 27컬럼 전체를 로딩했으나,
    // 화면이 쓰는 작성자 3컬럼(idx/username/location)만 SELECT 하도록 생성자 표현식으로 전환한다.
    // WHERE/ORDER는 대응하는 엔티티 쿼리와 동일. 리액션/첨부는 서비스가 배치로 사후 주입한다.
    String BOARD_LIST_ITEM_SELECT
            = "SELECT new com.linkup.Petory.domain.board.dto.BoardListItemDTO("
            + "  b.idx, b.title, b.content, b.category, b.status, b.createdAt, b.isDeleted, b.deletedAt, "
            + "  b.commentCount, b.likeCount, b.dislikeCount, b.viewCount, b.lastReactionAt, "
            + "  u.idx, u.username, u.location) "
            + "FROM Board b JOIN b.user u ";

    // 1단계: 커버링 인덱스로 깊은 skip. author_visible 로 걸러 users 조인이 필요없다.
    @Query(value = "SELECT idx FROM board WHERE is_deleted = 0 AND author_visible = 1 "
            + "ORDER BY created_at DESC LIMIT :size OFFSET :offset", nativeQuery = true)
    List<Long> findVisibleBoardIds(@Param("offset") long offset, @Param("size") int size);

    @Query(value = "SELECT idx FROM board WHERE category = :category AND is_deleted = 0 AND author_visible = 1 "
            + "ORDER BY created_at DESC LIMIT :size OFFSET :offset", nativeQuery = true)
    List<Long> findVisibleBoardIdsByCategory(@Param("category") String category,
            @Param("offset") long offset, @Param("size") int size);

    // 2단계: 살아남은 idx 만 projection 조립(작성자 조인 20건). ORDER 로 1단계 순서 재현.
    @Query(BOARD_LIST_ITEM_SELECT + "WHERE b.idx IN :ids ORDER BY b.createdAt DESC")
    List<BoardListItemDTO> findBoardListItemsByIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT COUNT(b) FROM Board b WHERE b.isDeleted = false AND b.authorVisible = true")
    long countVisible();

    @Query("SELECT COUNT(b) FROM Board b WHERE b.category = :category AND b.isDeleted = false AND b.authorVisible = true")
    long countVisibleByCategory(@Param("category") String category);

    @RepositoryMethod("게시글: 전체 목록 페이징 (지연 조인)")
    default Page<BoardListItemDTO> findBoardListItems(Pageable pageable) {
        List<Long> ids = findVisibleBoardIds(pageable.getOffset(), pageable.getPageSize());
        List<BoardListItemDTO> content = ids.isEmpty() ? List.of() : findBoardListItemsByIdIn(ids);
        return new PageImpl<>(content, pageable, countVisible());
    }

    @RepositoryMethod("게시글: 카테고리별 목록 페이징 (지연 조인)")
    default Page<BoardListItemDTO> findBoardListItemsByCategory(String category, Pageable pageable) {
        List<Long> ids = findVisibleBoardIdsByCategory(category, pageable.getOffset(), pageable.getPageSize());
        List<BoardListItemDTO> content = ids.isEmpty() ? List.of() : findBoardListItemsByIdIn(ids);
        return new PageImpl<>(content, pageable, countVisibleByCategory(category));
    }

    @RepositoryMethod("게시글: 작성자 닉네임 검색 페이징 (projection)")
    @Query(BOARD_LIST_ITEM_SELECT
            + "WHERE u.nickname LIKE :nickname% AND b.isDeleted = false AND u.isDeleted = false AND b.authorVisible = true ORDER BY b.createdAt DESC")
    Page<BoardListItemDTO> searchBoardListItemsByNickname(@Param("nickname") String nickname, Pageable pageable);

    @RepositoryMethod("게시글: 카테고리+기간별 조회")
    List<Board> findByCategoryAndCreatedAtBetween(String category, LocalDateTime start, LocalDateTime end);

    @RepositoryMethod("게시글: 기간별 통계")
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @RepositoryMethod("게시글: 관리자 전체 조회 (삭제 제외)")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false ORDER BY b.createdAt DESC")
    List<Board> findAllByIsDeletedFalseForAdmin();

    @RepositoryMethod("게시글: 관리자 전체 조회 (삭제 포함)")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u ORDER BY b.createdAt DESC")
    List<Board> findAllForAdmin();

    /**
     * 게시글 단건 조회 (작성자 포함, Fetch Join) [리팩토링] getBoard, getBoardForAdmin - Board
     * + User 1회 쿼리
     */
    @RepositoryMethod("게시글: idx로 조회 (작성자 포함)")
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.idx = :idx")
    Optional<Board> findByIdWithUser(@Param("idx") Long idx);

    @Transactional
    @Modifying
    @Query("UPDATE Board b SET b.viewCount = COALESCE(b.viewCount, 0) + 1 WHERE b.idx = :idx")
    void incrementViewCount(@Param("idx") Long idx);

    @Transactional
    @Modifying
    @Query("UPDATE Board b SET b.likeCount = GREATEST(0, COALESCE(b.likeCount, 0) + :delta) WHERE b.idx = :idx")
    void adjustLikeCount(@Param("idx") Long idx, @Param("delta") int delta);

    @Transactional
    @Modifying
    @Query("UPDATE Board b SET b.dislikeCount = GREATEST(0, COALESCE(b.dislikeCount, 0) + :delta) WHERE b.idx = :idx")
    void adjustDislikeCount(@Param("idx") Long idx, @Param("delta") int delta);

    @Transactional
    @Modifying
    @Query("UPDATE Board b SET b.commentCount = GREATEST(0, COALESCE(b.commentCount, 0) + :delta) WHERE b.idx = :idx")
    void adjustCommentCount(@Param("idx") Long idx, @Param("delta") int delta);

    @Transactional
    @Modifying
    @Query("UPDATE Board b SET b.lastReactionAt = :at WHERE b.idx = :idx")
    void updateLastReactionAt(@Param("idx") Long idx, @Param("at") java.time.LocalDateTime at);
}
