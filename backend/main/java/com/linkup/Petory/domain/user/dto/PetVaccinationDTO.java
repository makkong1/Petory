package com.linkup.Petory.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 애완동물 예방접종 기록 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetVaccinationDTO {
    private Long idx;
    private Long petIdx; // 소유 펫 ID
    private String vaccineName;
    private LocalDate vaccinatedAt;
    private LocalDate nextDue;
    private String notes;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

