package com.linkup.Petory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.entity.MissingPetBoard;
import com.linkup.Petory.entity.MissingPetStatus;

public interface MissingPetBoardRepository extends JpaRepository<MissingPetBoard, Long> {

    List<MissingPetBoard> findAllByOrderByCreatedAtDesc();

    List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(MissingPetStatus status);
}

