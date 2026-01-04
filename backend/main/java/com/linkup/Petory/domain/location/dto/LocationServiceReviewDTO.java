package com.linkup.Petory.domain.location.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationServiceReviewDTO {

    private Long idx;
    private Long serviceIdx;
    private Long userIdx;
    private String username; // 사용자명
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
}
