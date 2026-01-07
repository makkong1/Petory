package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Comment 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaCommentAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(Long id);

    boolean existsById(Long id);

    void delete(Comment comment);

    void deleteById(Long id);

    List<Comment> findByBoardOrderByCreatedAtAsc(Board board);

    /**
     * 삭제되지 않은 댓글만 조회 - 작성자도 활성 상태여야 함
     */
    List<Comment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(Board board);

    /**
     * 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
     */
    List<Comment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);

    /**
     * 여러 게시글의 댓글 카운트를 한 번에 조회 (배치 조회) - 작성자도 활성 상태여야 함
     * 반환값: List<Object[]> [boardId, count]
     */
    List<Object[]> countByBoardsAndIsDeletedFalse(List<Long> boardIds);

    /**
     * 관리자용: 작성자 상태 체크 없이 조회 (삭제된 사용자 댓글도 포함)
     */
    List<Comment> findByBoardAndIsDeletedFalseForAdmin(Board board);
}
