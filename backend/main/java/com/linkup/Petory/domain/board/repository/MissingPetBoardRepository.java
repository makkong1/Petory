package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * MissingPetBoard 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaMissingPetBoardAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface MissingPetBoardRepository {

    MissingPetBoard save(MissingPetBoard board);

    MissingPetBoard saveAndFlush(MissingPetBoard board);

    Optional<MissingPetBoard> findById(Long id);

    boolean existsById(Long id);

    void flush();

    void delete(MissingPetBoard board);

    void deleteById(Long id);

    List<MissingPetBoard> findAllByOrderByCreatedAtDesc();

    List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(MissingPetStatus status);

    Optional<MissingPetBoard> findByIdWithUser(Long id);

    /**
     * 댓글 포함 조회 (N+1 문제 해결) - 전체 조회
     */
    List<MissingPetBoard> findAllWithCommentsByOrderByCreatedAtDesc();

    /**
     * 댓글 포함 조회 (N+1 문제 해결) - 상태별 조회
     */
    List<MissingPetBoard> findByStatusWithCommentsOrderByCreatedAtDesc(MissingPetStatus status);

    /**
     * 댓글 포함 단건 조회 (N+1 문제 해결)
     */
    Optional<MissingPetBoard> findByIdWithComments(Long id);

    /**
     * 사용자별 게시글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
     */
    List<MissingPetBoard> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);
}
