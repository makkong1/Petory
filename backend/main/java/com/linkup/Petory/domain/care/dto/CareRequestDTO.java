package com.linkup.Petory.domain.care.dto;

import java.time.LocalDateTime;
import java.util.List;

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
    private String title;
    private String description;
    private LocalDateTime date;
    private Integer offeredCoins; // 제시한 코인 가격
    private String status; // OPEN, IN_PROGRESS, COMPLETED, CANCELLED
    private LocalDateTime createdAt;
    private Boolean deleted;
    private LocalDateTime deletedAt;

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