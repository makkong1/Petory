package com.linkup.Petory.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;

import lombok.RequiredArgsConstructor;

/**
 * PetRepository의 JPA 구현체(어댑터)입니다.
 * 
 * 이 클래스는 Spring Data JPA를 사용하여 PetRepository 인터페이스를 구현합니다.
 * 나중에 다른 DB나 DBMS로 변경할 경우, 이 어댑터와 유사한 새 클래스를 만들고
 * 
 * @Primary 어노테이션을 옮기면 됩니다.
 * 
 *          예시:
 *          - MyBatis로 변경: MyBatisPetAdapter 생성 후 @Primary 이동
 *          - MongoDB로 변경: MongoPetAdapter 생성 후 @Primary 이동
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaPetAdapter implements PetRepository {

    private final SpringDataJpaPetRepository jpaRepository;

    @Override
    public Pet save(Pet pet) {
        return jpaRepository.save(pet);
    }

    @Override
    public Optional<Pet> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(Pet pet) {
        jpaRepository.delete(pet);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Pet> findByUserIdAndNotDeleted(String userId) {
        return jpaRepository.findByUserIdAndNotDeleted(userId);
    }

    @Override
    public List<Pet> findByUserId(String userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public List<Pet> findByUserIdxAndNotDeleted(Long userIdx) {
        return jpaRepository.findByUserIdxAndNotDeleted(userIdx);
    }

    @Override
    public List<Pet> findByPetTypeAndIsDeletedFalse(PetType petType) {
        return jpaRepository.findByPetTypeAndIsDeletedFalse(petType);
    }

    @Override
    public List<Pet> findByPetNameContainingAndIsDeletedFalse(String petName) {
        return jpaRepository.findByPetNameContainingAndIsDeletedFalse(petName);
    }
}

