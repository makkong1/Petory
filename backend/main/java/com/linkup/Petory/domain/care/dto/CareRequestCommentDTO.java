package com.linkup.Petory.domain.care.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.file.dto.FileDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
/**
 * 펫케어 요청 댓글 응답/요청 DTO.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareRequestCommentDTO {

    private Long idx;
    @NotBlank
    private String content;
    private LocalDateTime createdAt;
    private String status; // ACTIVE / BLINDED / DELETED
    private Boolean deleted;
    private LocalDateTime deletedAt;

    // 펫케어 요청 정보
    private Long careRequestId;

    // 작성자 정보
    private Long userId;
    private String username;
    private String nickname;
    private String userLocation;
    private String userRole; // SERVICE_PROVIDER 체크용

    private List<FileDTO> attachments;
}
