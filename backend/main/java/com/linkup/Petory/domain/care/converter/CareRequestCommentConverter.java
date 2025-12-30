package com.linkup.Petory.domain.care.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.care.dto.CareRequestCommentDTO;
import com.linkup.Petory.domain.care.entity.CareRequestComment;

@Component
public class CareRequestCommentConverter {

    public CareRequestCommentDTO toDTO(CareRequestComment comment) {
        return CareRequestCommentDTO.builder()
                .idx(comment.getIdx())
                .careRequestId(comment.getCareRequest().getIdx())
                .userId(comment.getUser().getIdx())
                .username(comment.getUser().getUsername())
                .nickname(comment.getUser().getNickname())
                .userLocation(comment.getUser().getLocation())
                .userRole(comment.getUser().getRole().name())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .status(comment.getIsDeleted() ? "DELETED" : "ACTIVE")
                .deleted(comment.getIsDeleted())
                .deletedAt(comment.getDeletedAt())
                .build();
    }
}
