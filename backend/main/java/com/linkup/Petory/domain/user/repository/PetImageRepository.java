package com.linkup.Petory.domain.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.PetImage;

@Repository
public interface PetImageRepository extends JpaRepository<PetImage, Long> {

    /**
     * 펫 ID로 이미지 목록 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT pi FROM PetImage pi WHERE pi.pet.idx = :petIdx AND pi.isDeleted = false ORDER BY pi.createdAt ASC")
    List<PetImage> findByPetIdxAndNotDeleted(@Param("petIdx") Long petIdx);

    /**
     * 펫 ID로 이미지 목록 조회 (모든 것)
     */
    List<PetImage> findByPetIdx(Long petIdx);
}

