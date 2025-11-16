package com.linkup.Petory.domain.board.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;

public interface MissingPetCommentRepository extends JpaRepository<MissingPetComment, Long> {

    List<MissingPetComment> findByBoardOrderByCreatedAtAsc(MissingPetBoard board);

    // soft-deleted 제외
    List<MissingPetComment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(MissingPetBoard board);
}
