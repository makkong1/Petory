package com.linkup.Petory.domain.user.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.dto.PetDTO;
import com.linkup.Petory.domain.user.dto.PetImageDTO;
import com.linkup.Petory.domain.user.dto.PetVaccinationDTO;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;
import com.linkup.Petory.domain.user.entity.PetGender;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PetConverter {

    private final PetImageConverter petImageConverter;
    private final PetVaccinationConverter petVaccinationConverter;

    /**
     * Entity → DTO 변환
     */
    public PetDTO toDTO(Pet pet) {
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
                .profileImageUrl(pet.getProfileImageUrl())
                .isDeleted(pet.getIsDeleted())
                .createdAt(pet.getCreatedAt())
                .updatedAt(pet.getUpdatedAt())
                .deletedAt(pet.getDeletedAt());

        // 연관 데이터 변환 (있는 경우만)
        if (pet.getImages() != null && !pet.getImages().isEmpty()) {
            builder.images(pet.getImages().stream()
                    .map(petImageConverter::toDTO)
                    .collect(Collectors.toList()));
        }

        if (pet.getVaccinations() != null && !pet.getVaccinations().isEmpty()) {
            builder.vaccinations(pet.getVaccinations().stream()
                    .map(petVaccinationConverter::toDTO)
                    .collect(Collectors.toList()));
        }

        return builder.build();
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
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            Pet pet = builder.build();
            pet.setImages(dto.getImages().stream()
                    .map(imgDto -> petImageConverter.toEntity(imgDto, pet))
                    .collect(Collectors.toList()));
            return pet;
        }

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
     */
    public List<PetDTO> toDTOList(List<Pet> pets) {
        return pets.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<Pet> toEntityList(List<PetDTO> dtos) {
        return dtos.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }
}

