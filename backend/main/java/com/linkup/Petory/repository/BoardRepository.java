package com.linkup.Petory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.entity.Board;
import com.linkup.Petory.entity.Users;

@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {

    // 전체 게시글 조회 (최신순)
    List<Board> findAllByOrderByCreatedAtDesc();

    // 카테고리별 게시글 조회 (최신순)
    List<Board> findByCategoryOrderByCreatedAtDesc(String category);

    // 사용자별 게시글 조회 (최신순)
    List<Board> findByUserOrderByCreatedAtDesc(Users user);

    // 제목이나 내용에 키워드 포함된 게시글 검색 (최신순)
    List<Board> findByTitleContainingOrContentContainingOrderByCreatedAtDesc(String titleKeyword,
            String contentKeyword);
}
