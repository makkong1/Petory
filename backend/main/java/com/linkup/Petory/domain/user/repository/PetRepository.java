package com.linkup.Petory.domain.user.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;

/**
 * Pet 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaPetAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface PetRepository {

    Pet save(Pet pet);

    Optional<Pet> findById(Long id);

    void delete(Pet pet);

    void deleteById(Long id);

    /**
     * 사용자 ID로 펫 목록 조회 (삭제되지 않은 것만)
     */
    List<Pet> findByUserIdAndNotDeleted(String userId);

    /**
     * 사용자 ID로 펫 목록 조회 (모든 것)
     */
    List<Pet> findByUserId(String userId);

    /**
     * 사용자 idx로 펫 목록 조회 (삭제되지 않은 것만)
     */
    List<Pet> findByUserIdxAndNotDeleted(Long userIdx);

    /**
     * 펫 타입으로 조회 (삭제되지 않은 것만)
     */
    List<Pet> findByPetTypeAndIsDeletedFalse(PetType petType);

    /**
     * 펫 이름으로 조회 (삭제되지 않은 것만)
     */
    List<Pet> findByPetNameContainingAndIsDeletedFalse(String petName);
}

