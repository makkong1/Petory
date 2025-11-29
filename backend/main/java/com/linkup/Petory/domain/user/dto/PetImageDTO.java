package com.linkup.Petory.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 애완동물 이미지 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetImageDTO {
    private Long idx;
    private Long petIdx; // 소유 펫 ID
    private String imageUrl;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

