package com.linkup.Petory.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
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
    private final AttachmentFileService attachmentFileService;

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

        // ETC 타입일 때 breed 필수 검증
        if (dto.getPetType() != null && PetType.valueOf(dto.getPetType()) == PetType.ETC) {
            if (dto.getBreed() == null || dto.getBreed().trim().isEmpty()) {
                throw new RuntimeException("기타 종류를 선택한 경우, 구체적인 종류를 입력해주세요.");
            }
        }

        // DTO → Entity 변환
        Pet pet = petConverter.toEntity(dto);
        pet.setUser(user);
        pet.setIsDeleted(false);

        Pet saved = petRepository.save(pet);
        
        // 펫 이미지 파일 동기화
        if (dto.getProfileImageUrl() != null && !dto.getProfileImageUrl().trim().isEmpty()) {
            attachmentFileService.syncSingleAttachment(
                FileTargetType.PET, 
                saved.getIdx(), 
                dto.getProfileImageUrl(), 
                null
            );
        }
        
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
            PetType newPetType = PetType.valueOf(dto.getPetType());
            pet.setPetType(newPetType);
            
            // ETC 타입으로 변경하거나, 이미 ETC인데 breed가 비어있으면 검증
            if (newPetType == PetType.ETC) {
                String breed = dto.getBreed() != null ? dto.getBreed() : pet.getBreed();
                if (breed == null || breed.trim().isEmpty()) {
                    throw new RuntimeException("기타 종류를 선택한 경우, 구체적인 종류를 입력해주세요.");
                }
            }
        }
        if (dto.getBreed() != null) {
            pet.setBreed(dto.getBreed());
        }
        
        // ETC 타입인데 breed가 비어있는 경우 재검증
        if (pet.getPetType() == PetType.ETC && (pet.getBreed() == null || pet.getBreed().trim().isEmpty())) {
            throw new RuntimeException("기타 종류를 선택한 경우, 구체적인 종류를 입력해주세요.");
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
        
        // 펫 이미지 파일 동기화
        // DTO에 profileImageUrl이 명시적으로 전달된 경우에만 동기화
        if (dto.getProfileImageUrl() != null) {
            String imageUrl = dto.getProfileImageUrl().trim();
            if (imageUrl.isEmpty()) {
                // 빈 문자열인 경우 File 테이블에서 삭제
                attachmentFileService.deleteAll(FileTargetType.PET, updated.getIdx());
            } else {
                // 이미지 URL이 있는 경우 File 테이블에 동기화
                attachmentFileService.syncSingleAttachment(
                    FileTargetType.PET, 
                    updated.getIdx(), 
                    imageUrl, 
                    null
                );
            }
        }
        // DTO에 profileImageUrl이 null인 경우는 기존 값 유지 (File 테이블도 그대로 유지)
        
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
        
        // 펫 이미지 파일 삭제 (소프트 삭제이므로 파일은 유지하되 필요시 삭제 가능)
        // attachmentFileService.deleteAll(FileTargetType.PET, petIdx);
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

