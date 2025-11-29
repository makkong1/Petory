package com.linkup.Petory.domain.user.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.dto.PetVaccinationDTO;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetVaccination;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PetVaccinationConverter {

    /**
     * Entity → DTO 변환
     */
    public PetVaccinationDTO toDTO(PetVaccination petVaccination) {
        return PetVaccinationDTO.builder()
                .idx(petVaccination.getIdx())
                .petIdx(petVaccination.getPet() != null ? petVaccination.getPet().getIdx() : null)
                .vaccineName(petVaccination.getVaccineName())
                .vaccinatedAt(petVaccination.getVaccinatedAt())
                .nextDue(petVaccination.getNextDue())
                .notes(petVaccination.getNotes())
                .isDeleted(petVaccination.getIsDeleted())
                .createdAt(petVaccination.getCreatedAt())
                .updatedAt(petVaccination.getUpdatedAt())
                .build();
    }

    /**
     * DTO → Entity 변환
     */
    public PetVaccination toEntity(PetVaccinationDTO dto, Pet pet) {
        return PetVaccination.builder()
                .idx(dto.getIdx())
                .pet(pet)
                .vaccineName(dto.getVaccineName())
                .vaccinatedAt(dto.getVaccinatedAt())
                .nextDue(dto.getNextDue())
                .notes(dto.getNotes())
                .isDeleted(dto.getIsDeleted())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    /**
     * 리스트 변환
     */
    public List<PetVaccinationDTO> toDTOList(List<PetVaccination> petVaccinations) {
        return petVaccinations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PetVaccination> toEntityList(List<PetVaccinationDTO> dtos, Pet pet) {
        return dtos.stream()
                .map(dto -> toEntity(dto, pet))
                .collect(Collectors.toList());
    }
}

