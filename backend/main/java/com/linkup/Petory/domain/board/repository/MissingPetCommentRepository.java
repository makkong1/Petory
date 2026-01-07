package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

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
     * 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
     */
    List<MissingPetComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);
}
