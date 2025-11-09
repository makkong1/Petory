package com.linkup.Petory.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.dto.CommentDTO;
import com.linkup.Petory.entity.Comment;

@Component
public class CommentConverter {

    public CommentDTO toDTO(Comment comment) {
        return CommentDTO.builder()
                .idx(comment.getIdx())
                .boardId(comment.getBoard().getIdx())
                .userId(comment.getUser().getIdx())
                .username(comment.getUser().getUsername())
                .userLocation(comment.getUser().getLocation())
                .commentFilePath(comment.getCommentFilePath())
                .likeCount(0)
                .dislikeCount(0)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
