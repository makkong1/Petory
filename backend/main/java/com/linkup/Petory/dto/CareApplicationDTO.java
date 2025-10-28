package com.linkup.Petory.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareApplicationDTO {
    private Long idx;
    private Long careRequestId;
    private Long providerId;
    private String providerName;
    private String status;
    private String message;
    private List<CareReviewDTO> reviews;
}
