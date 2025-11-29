package com.linkup.Petory.domain.user.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.dto.PetImageDTO;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetImage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PetImageConverter {

    /**
     * Entity → DTO 변환
     */
    public PetImageDTO toDTO(PetImage petImage) {
        return PetImageDTO.builder()
                .idx(petImage.getIdx())
                .petIdx(petImage.getPet() != null ? petImage.getPet().getIdx() : null)
                .imageUrl(petImage.getImageUrl())
                .isDeleted(petImage.getIsDeleted())
                .createdAt(petImage.getCreatedAt())
                .updatedAt(petImage.getUpdatedAt())
                .build();
    }

    /**
     * DTO → Entity 변환
     */
    public PetImage toEntity(PetImageDTO dto, Pet pet) {
        return PetImage.builder()
                .idx(dto.getIdx())
                .pet(pet)
                .imageUrl(dto.getImageUrl())
                .isDeleted(dto.getIsDeleted())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    /**
     * 리스트 변환
     */
    public List<PetImageDTO> toDTOList(List<PetImage> petImages) {
        return petImages.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PetImage> toEntityList(List<PetImageDTO> dtos, Pet pet) {
        return dtos.stream()
                .map(dto -> toEntity(dto, pet))
                .collect(Collectors.toList());
    }
}

