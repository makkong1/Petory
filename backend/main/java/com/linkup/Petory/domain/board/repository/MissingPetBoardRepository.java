package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;

public interface MissingPetBoardRepository extends JpaRepository<MissingPetBoard, Long> {

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findAllByOrderByCreatedAtDesc();

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(@Param("status") MissingPetStatus status);

    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user WHERE b.idx = :id")
    Optional<MissingPetBoard> findByIdWithUser(@Param("id") Long id);
}
