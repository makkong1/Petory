package com.linkup.Petory.domain.user.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaPetAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaPetRepository extends JpaRepository<Pet, Long> {

    @RepositoryMethod("펫: 사용자 ID로 목록 조회 (삭제 제외)")
    @Query("SELECT p FROM Pet p WHERE p.user.id = :userId AND p.isDeleted = false")
    List<Pet> findByUserIdAndNotDeleted(@Param("userId") String userId);

    @RepositoryMethod("펫: 사용자 idx로 목록 조회 (삭제 제외)")
    @Query("SELECT p FROM Pet p WHERE p.user.idx = :userIdx AND p.isDeleted = false")
    List<Pet> findByUserIdxAndNotDeleted(@Param("userIdx") Long userIdx);

    // 파생 쿼리(메서드명)로는 주인 상태를 볼 수 없어 @Query 로 전환했다.
    // 공개 목록(/api/pets/type/{type})이므로 탈퇴·영구정지 회원의 펫은 제외한다(정지는 보임).
    @RepositoryMethod("펫: 타입별 조회")
    @Query("SELECT p FROM Pet p JOIN p.user u WHERE p.petType = :petType AND p.isDeleted = false "
            + "AND u.isDeleted = false AND u.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED")
    List<Pet> findByPetTypeAndIsDeletedFalse(@Param("petType") PetType petType);

    @RepositoryMethod("펫: 타입별 페이징 조회")
    @Query(value = "SELECT p FROM Pet p JOIN p.user u WHERE p.petType = :petType AND p.isDeleted = false "
            + "AND u.isDeleted = false AND u.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED",
           countQuery = "SELECT COUNT(p) FROM Pet p JOIN p.user u WHERE p.petType = :petType AND p.isDeleted = false "
            + "AND u.isDeleted = false AND u.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED")
    Page<Pet> findByPetTypeAndIsDeletedFalse(@Param("petType") PetType petType, Pageable pageable);
}

