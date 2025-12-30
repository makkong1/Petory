package com.linkup.Petory.domain.user.converter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.user.dto.PetDTO;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;
import com.linkup.Petory.domain.user.entity.PetGender;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PetConverter {

    private final PetVaccinationConverter petVaccinationConverter;
    private final AttachmentFileService attachmentFileService;

    /**
     * Entity → DTO 변환
     * @param pet 변환할 Pet 엔티티
     * @param profileImageUrl 미리 조회한 프로필 이미지 URL (null이면 pet.getProfileImageUrl() 사용)
     */
    public PetDTO toDTO(Pet pet, String profileImageUrl) {
        // profileImageUrl이 제공되지 않으면 기존 값 사용
        String finalProfileImageUrl = profileImageUrl != null ? profileImageUrl : pet.getProfileImageUrl();

        PetDTO.PetDTOBuilder builder = PetDTO.builder()
                .idx(pet.getIdx())
                .userIdx(pet.getUser() != null ? pet.getUser().getIdx() : null)
                .petName(pet.getPetName())
                .petType(pet.getPetType() != null ? pet.getPetType().name() : null)
                .breed(pet.getBreed())
                .gender(pet.getGender() != null ? pet.getGender().name() : null)
                .age(pet.getAge())
                .color(pet.getColor())
                .weight(pet.getWeight())
                .birthDate(pet.getBirthDate())
                .isNeutered(pet.getIsNeutered())
                .healthInfo(pet.getHealthInfo())
                .specialNotes(pet.getSpecialNotes())
                .profileImageUrl(finalProfileImageUrl)
                .isDeleted(pet.getIsDeleted())
                .createdAt(pet.getCreatedAt())
                .updatedAt(pet.getUpdatedAt())
                .deletedAt(pet.getDeletedAt());

        // 연관 데이터 변환 (있는 경우만)
        if (pet.getVaccinations() != null && !pet.getVaccinations().isEmpty()) {
            builder.vaccinations(pet.getVaccinations().stream()
                    .map(petVaccinationConverter::toDTO)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    /**
     * Entity → DTO 변환 (단일 객체용, File 개별 조회)
     * [주의] 리스트 변환 시에는 toDTOList() 사용 권장 (배치 조회로 N+1 방지)
     */
    public PetDTO toDTO(Pet pet) {
        // File 테이블에서 펫 이미지 가져오기
        String profileImageUrl = pet.getProfileImageUrl(); // 기존 값 유지 (하위 호환성)
        try {
            List<FileDTO> files = attachmentFileService.getAttachments(FileTargetType.PET, pet.getIdx());
            if (!files.isEmpty()) {
                // File 테이블에 이미지가 있으면 우선 사용
                profileImageUrl = files.get(0).getDownloadUrl();
            }
        } catch (Exception e) {
            // File 테이블 조회 실패 시 기존 profileImageUrl 사용
        }
        return toDTO(pet, profileImageUrl);
    }

    /**
     * DTO → Entity 변환
     */
    public Pet toEntity(PetDTO dto) {
        Pet.PetBuilder builder = Pet.builder()
                .idx(dto.getIdx())
                .petName(dto.getPetName())
                .petType(dto.getPetType() != null ? PetType.valueOf(dto.getPetType()) : null)
                .breed(dto.getBreed())
                .gender(dto.getGender() != null ? PetGender.valueOf(dto.getGender()) : null)
                .age(dto.getAge())
                .color(dto.getColor())
                .weight(dto.getWeight())
                .birthDate(dto.getBirthDate())
                .isNeutered(dto.getIsNeutered())
                .healthInfo(dto.getHealthInfo())
                .specialNotes(dto.getSpecialNotes())
                .profileImageUrl(dto.getProfileImageUrl())
                .isDeleted(dto.getIsDeleted())
                .deletedAt(dto.getDeletedAt());

        // 연관 데이터 변환 (있는 경우만)
        if (dto.getVaccinations() != null && !dto.getVaccinations().isEmpty()) {
            Pet pet = builder.build();
            pet.setVaccinations(dto.getVaccinations().stream()
                    .map(vacDto -> petVaccinationConverter.toEntity(vacDto, pet))
                    .collect(Collectors.toList()));
            return pet;
        }

        return builder.build();
    }

    /**
     * 리스트 변환
     * [2단계 최적화] File N+1 문제 해결: 배치 조회 사용
     */
    public List<PetDTO> toDTOList(List<Pet> pets) {
        if (pets == null || pets.isEmpty()) {
            return List.of();
        }
        
        // 모든 Pet의 idx 수집
        List<Long> petIndices = pets.stream()
                .map(Pet::getIdx)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        // 한 번에 모든 File 조회 (배치 조회)
        Map<Long, List<FileDTO>> filesByPetIdx = Map.of();
        if (!petIndices.isEmpty()) {
            try {
                filesByPetIdx = attachmentFileService.getAttachmentsBatch(FileTargetType.PET, petIndices);
            } catch (Exception e) {
                // 배치 조회 실패 시 빈 Map 사용 (개별 조회로 fallback)
            }
        }
        
        // 각 Pet을 변환하면서 미리 조회한 File 사용
        final Map<Long, List<FileDTO>> finalFilesByPetIdx = filesByPetIdx;
        return pets.stream()
                .map(pet -> {
                    // 배치 조회한 File 정보 사용
                    List<FileDTO> files = finalFilesByPetIdx.getOrDefault(pet.getIdx(), List.of());
                    String profileImageUrl = null;
                    if (!files.isEmpty()) {
                        profileImageUrl = files.get(0).getDownloadUrl();
                    }
                    // File 정보를 파라미터로 전달하여 개별 조회 방지
                    return toDTO(pet, profileImageUrl);
                })
                .collect(Collectors.toList());
    }

    public List<Pet> toEntityList(List<PetDTO> dtos) {
        return dtos.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }
}
