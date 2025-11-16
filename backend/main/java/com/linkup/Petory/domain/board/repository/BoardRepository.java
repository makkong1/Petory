package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.board.entity.Board;

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

    // 제목이나 내용에 키워드 포함된 게시글 검색 (최신순)
    List<Board> findByTitleContainingOrContentContainingOrderByCreatedAtDesc(String titleKeyword,
            String contentKeyword);

    // 제목/내용 키워드 + 삭제되지 않은 게시글 검색 (최신순)
    List<Board> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseAndIsDeletedFalseOrderByCreatedAtDesc(
            String titleKeyword, String contentKeyword);

    // 카테고리 + 기간별 조회
    List<Board> findByCategoryAndCreatedAtBetween(String category, LocalDateTime start, LocalDateTime end);
}
