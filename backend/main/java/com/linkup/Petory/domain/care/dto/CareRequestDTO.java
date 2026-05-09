package com.linkup.Petory.domain.care.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.linkup.Petory.domain.user.dto.PetDTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareRequestDTO {
    private Long idx;
    @NotBlank private String title;
    @NotBlank private String description;
    @NotNull private LocalDateTime date;
    @NotNull @Min(1) private Integer offeredCoins;
    private String status; // OPEN, IN_PROGRESS, COMPLETED, CANCELLED
    private LocalDateTime createdAt;
    private Boolean deleted;
    private LocalDateTime deletedAt;

    // 위치 정보
    private Double latitude;
    private Double longitude;
    private String address;

    // 요청자 정보
    private Long userId;
    private String username;
    private String userLocation;

    // 관련 펫 정보 (선택사항)
    private Long petIdx;
    private PetDTO pet;

    // 지원자 정보
    private List<CareApplicationDTO> applications;
    private Integer applicationCount;

    // 댓글 정보
    private List<CareRequestCommentDTO> comments;
    private Integer commentCount;
}