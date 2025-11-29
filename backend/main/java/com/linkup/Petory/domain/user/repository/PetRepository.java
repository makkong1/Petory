package com.linkup.Petory.domain.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;

@Repository
public interface PetRepository extends JpaRepository<Pet, Long> {

    /**
     * 사용자 ID로 펫 목록 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT p FROM Pet p WHERE p.user.id = :userId AND p.isDeleted = false")
    List<Pet> findByUserIdAndNotDeleted(@Param("userId") String userId);

    /**
     * 사용자 ID로 펫 목록 조회 (모든 것)
     */
    @Query("SELECT p FROM Pet p WHERE p.user.id = :userId")
    List<Pet> findByUserId(@Param("userId") String userId);

    /**
     * 사용자 idx로 펫 목록 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT p FROM Pet p WHERE p.user.idx = :userIdx AND p.isDeleted = false")
    List<Pet> findByUserIdxAndNotDeleted(@Param("userIdx") Long userIdx);

    /**
     * 펫 타입으로 조회 (삭제되지 않은 것만)
     */
    List<Pet> findByPetTypeAndIsDeletedFalse(PetType petType);

    /**
     * 펫 이름으로 조회 (삭제되지 않은 것만)
     */
    List<Pet> findByPetNameContainingAndIsDeletedFalse(String petName);
}

