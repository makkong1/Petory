package com.linkup.Petory.domain.location.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationServiceReviewDTO {

    private Long idx;
    private Long serviceIdx;
    private String serviceName;
    private Long userIdx;
    private String username;
    @NotNull @Min(1) @Max(5) private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
}
