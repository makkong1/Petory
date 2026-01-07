package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Board 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaBoardAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface BoardRepository {

    // 기본 CRUD 메서드
    Board save(Board board);

    Board saveAndFlush(Board board);

    List<Board> saveAll(List<Board> boards);

    Optional<Board> findById(Long id);

    boolean existsById(Long id);

    List<Board> findAll();

    void delete(Board board);

    void deleteById(Long id);

        // Specification을 사용한 동적 쿼리 (JPA 구현체에서 사용)
        List<Board> findAll(Specification<Board> spec);

        Page<Board> findAll(Specification<Board> spec, Pageable pageable);

        // 전체 게시글 조회 (최신순)
        List<Board> findAllByOrderByCreatedAtDesc();

        // 전체 게시글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
        List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();

        // 전체 게시글 조회 (삭제되지 않은 것만, 최신순) - 페이징 - 작성자도 활성 상태여야 함
        Page<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

        // 카테고리별 게시글 조회 (최신순)
        List<Board> findByCategoryOrderByCreatedAtDesc(String category);

        // 카테고리별 삭제되지 않은 게시글 조회 (최신순) - 작성자도 활성 상태여야 함
        List<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category);

        // 카테고리별 삭제되지 않은 게시글 조회 (최신순) - 페이징 - 작성자도 활성 상태여야 함
        Page<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category, Pageable pageable);

        // 사용자별 게시글 조회 (최신순)
        List<Board> findByUserOrderByCreatedAtDesc(Users user);

        // 사용자별 삭제되지 않은 게시글 조회 (최신순) - 작성자도 활성 상태여야 함
        List<Board> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);

        // 제목으로 검색 - 작성자도 활성 상태여야 함
        List<Board> findByTitleContainingAndIsDeletedFalseOrderByCreatedAtDesc(String title);

        // 제목으로 검색 (페이징) - 작성자도 활성 상태여야 함
        Page<Board> findByTitleContainingAndIsDeletedFalseOrderByCreatedAtDesc(String title, Pageable pageable);

        // 내용으로 검색 - 작성자도 활성 상태여야 함
        List<Board> findByContentContainingAndIsDeletedFalseOrderByCreatedAtDesc(String content);

        // 내용으로 검색 (페이징) - 작성자도 활성 상태여야 함
        Page<Board> findByContentContainingAndIsDeletedFalseOrderByCreatedAtDesc(String content, Pageable pageable);

        // FULLTEXT 인덱스 사용 쿼리 (제목+내용) - 페이징 - 작성자도 활성 상태여야 함
        Page<Board> searchByKeywordWithPaging(String keyword, Pageable pageable);

        // 카테고리 + 기간별 조회
        List<Board> findByCategoryAndCreatedAtBetween(String category, LocalDateTime start, LocalDateTime end);

        // 통계용
        long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

        // 관리자용: 작성자 상태 체크 없이 조회 (삭제된 사용자 콘텐츠도 포함) - 페이징
        Page<Board> findAllByIsDeletedFalseForAdmin(Pageable pageable);

        // 관리자용: 작성자 상태 체크 없이 조회 (삭제된 사용자 콘텐츠도 포함) - 전체 조회
        List<Board> findAllByIsDeletedFalseForAdmin();

        // 관리자용: 전체 조회 (삭제 포함)
        List<Board> findAllForAdmin();

        // 관리자용: 카테고리별 조회 (페이징)
        Page<Board> findByCategoryAndIsDeletedFalseForAdmin(String category, Pageable pageable);
}
