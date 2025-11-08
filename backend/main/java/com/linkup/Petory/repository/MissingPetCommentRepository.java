package com.linkup.Petory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.entity.MissingPetBoard;
import com.linkup.Petory.entity.MissingPetComment;

public interface MissingPetCommentRepository extends JpaRepository<MissingPetComment, Long> {

    List<MissingPetComment> findByBoardOrderByCreatedAtAsc(MissingPetBoard board);
}

