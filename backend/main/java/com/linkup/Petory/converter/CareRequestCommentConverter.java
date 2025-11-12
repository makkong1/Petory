package com.linkup.Petory.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.dto.CareRequestCommentDTO;
import com.linkup.Petory.entity.CareRequestComment;

@Component
public class CareRequestCommentConverter {

    public CareRequestCommentDTO toDTO(CareRequestComment comment) {
        return CareRequestCommentDTO.builder()
                .idx(comment.getIdx())
                .careRequestId(comment.getCareRequest().getIdx())
                .userId(comment.getUser().getIdx())
                .username(comment.getUser().getUsername())
                .userLocation(comment.getUser().getLocation())
                .userRole(comment.getUser().getRole().name())
                .commentFilePath(comment.getCommentFilePath())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}

