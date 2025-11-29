package com.linkup.Petory.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.user.converter.PetConverter;
import com.linkup.Petory.domain.user.dto.PetDTO;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.PetRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PetService {

    private final PetRepository petRepository;
    private final PetConverter petConverter;
    private final UsersRepository usersRepository;

    /**
     * 사용자의 모든 펫 조회 (삭제되지 않은 것만)
     */
    @Transactional(readOnly = true)
    public List<PetDTO> getPetsByUserId(String userId) {
        List<Pet> pets = petRepository.findByUserIdAndNotDeleted(userId);
        return petConverter.toDTOList(pets);
    }

    /**
     * 사용자 idx로 펫 목록 조회 (삭제되지 않은 것만)
     */
    @Transactional(readOnly = true)
    public List<PetDTO> getPetsByUserIdx(Long userIdx) {
        List<Pet> pets = petRepository.findByUserIdxAndNotDeleted(userIdx);
        return petConverter.toDTOList(pets);
    }

    /**
     * 펫 단일 조회
     */
    @Transactional(readOnly = true)
    public PetDTO getPet(Long petIdx) {
        Pet pet = petRepository.findById(petIdx)
                .orElseThrow(() -> new RuntimeException("Pet not found"));
        
        if (pet.getIsDeleted()) {
            throw new RuntimeException("Pet is deleted");
        }
        
        return petConverter.toDTO(pet);
    }

    /**
     * 펫 생성
     */
    public PetDTO createPet(String userId, PetDTO dto) {
        // 사용자 조회
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // DTO → Entity 변환
        Pet pet = petConverter.toEntity(dto);
        pet.setUser(user);
        pet.setIsDeleted(false);

        Pet saved = petRepository.save(pet);
        return petConverter.toDTO(saved);
    }

    /**
     * 펫 수정
     */
    public PetDTO updatePet(Long petIdx, PetDTO dto) {
        Pet pet = petRepository.findById(petIdx)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        if (pet.getIsDeleted()) {
            throw new RuntimeException("Pet is deleted");
        }

        // 필드 업데이트
        if (dto.getPetName() != null) {
            pet.setPetName(dto.getPetName());
        }
        if (dto.getPetType() != null) {
            pet.setPetType(PetType.valueOf(dto.getPetType()));
        }
        if (dto.getBreed() != null) {
            pet.setBreed(dto.getBreed());
        }
        if (dto.getGender() != null) {
            pet.setGender(com.linkup.Petory.domain.user.entity.PetGender.valueOf(dto.getGender()));
        }
        if (dto.getAge() != null) {
            pet.setAge(dto.getAge());
        }
        if (dto.getColor() != null) {
            pet.setColor(dto.getColor());
        }
        if (dto.getWeight() != null) {
            pet.setWeight(dto.getWeight());
        }
        if (dto.getBirthDate() != null) {
            pet.setBirthDate(dto.getBirthDate());
        }
        if (dto.getIsNeutered() != null) {
            pet.setIsNeutered(dto.getIsNeutered());
        }
        if (dto.getHealthInfo() != null) {
            pet.setHealthInfo(dto.getHealthInfo());
        }
        if (dto.getSpecialNotes() != null) {
            pet.setSpecialNotes(dto.getSpecialNotes());
        }
        if (dto.getProfileImageUrl() != null) {
            pet.setProfileImageUrl(dto.getProfileImageUrl());
        }

        Pet updated = petRepository.save(pet);
        return petConverter.toDTO(updated);
    }

    /**
     * 펫 삭제 (소프트 삭제)
     */
    public void deletePet(Long petIdx) {
        Pet pet = petRepository.findById(petIdx)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setIsDeleted(true);
        pet.setDeletedAt(java.time.LocalDateTime.now());
        petRepository.save(pet);
    }

    /**
     * 펫 복구
     */
    public PetDTO restorePet(Long petIdx) {
        Pet pet = petRepository.findById(petIdx)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setIsDeleted(false);
        pet.setDeletedAt(null);
        Pet restored = petRepository.save(pet);
        return petConverter.toDTO(restored);
    }

    /**
     * 펫 타입으로 조회
     */
    @Transactional(readOnly = true)
    public List<PetDTO> getPetsByType(String petType) {
        PetType type = PetType.valueOf(petType);
        List<Pet> pets = petRepository.findByPetTypeAndIsDeletedFalse(type);
        return petConverter.toDTOList(pets);
    }
}

