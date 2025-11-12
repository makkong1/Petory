package com.linkup.Petory.dto;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareRequestCommentDTO {
    private Long idx;
    private String content;
    private LocalDateTime createdAt;

    // 펫케어 요청 정보
    private Long careRequestId;

    // 작성자 정보
    private Long userId;
    private String username;
    private String userLocation;
    private String userRole; // SERVICE_PROVIDER 체크용

    private String commentFilePath;
}

