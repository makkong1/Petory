package com.linkup.Petory.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 애완동물 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetDTO {
    private Long idx;
    private Long userIdx;
    @NotBlank private String petName;
    @NotBlank private String petType;
    private String breed;
    private String gender; // M, F, UNKNOWN
    private String age;
    private String color;
    private BigDecimal weight;
    private LocalDate birthDate;
    private Boolean isNeutered;
    private String healthInfo;
    private String specialNotes;
    private String profileImageUrl;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    
    // 연관 데이터 (선택적)
    private List<PetVaccinationDTO> vaccinations;
}

