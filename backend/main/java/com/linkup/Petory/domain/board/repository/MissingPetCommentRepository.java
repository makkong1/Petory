package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * MissingPetComment 도메인 Repository 인터페이스입니다.
 */
public interface MissingPetCommentRepository {

    MissingPetComment save(MissingPetComment comment);

    MissingPetComment saveAndFlush(MissingPetComment comment);

    Optional<MissingPetComment> findById(Long id);

    boolean existsById(Long id);

    List<MissingPetComment> findAll();

    void flush();

    void delete(MissingPetComment comment);

    void deleteById(Long id);

    List<MissingPetComment> findByBoardOrderByCreatedAtAsc(MissingPetBoard board);

    /**
     * soft-deleted 제외 - 작성자도 활성 상태여야 함
     */
    List<MissingPetComment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(MissingPetBoard board);

    /**
     * 게시글의 댓글 수 조회 (COUNT 쿼리 - N건 로드 방지)
     * [리팩토링] findByBoard + size() → COUNT 쿼리 1회
     */
    long countByBoardAndIsDeletedFalse(MissingPetBoard board);

    /**
     * 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
     */
    List<MissingPetComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);

    /**
     * 게시글별 댓글 수 배치 조회 (N+1 문제 해결)
     * @param boardIds 게시글 ID 목록
     * @return [게시글 ID, 댓글 수] 쌍의 리스트
     */
    List<Object[]> countCommentsByBoardIds(List<Long> boardIds);

    /**
     * 페이징 지원 - 게시글별 댓글 조회
     */
    Page<MissingPetComment> findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(Long boardId, Pageable pageable);

    /**
     * 게시글의 모든 미삭제 댓글 일괄 소프트 삭제 (배치 UPDATE)
     * [리팩토링] N건 루프 save → 1회 UPDATE 쿼리 (missing-pet-backend-performance-optimization.md)
     * @return 업데이트된 행 수
     */
    int softDeleteAllByBoardIdx(Long boardIdx, LocalDateTime deletedAt);
}
