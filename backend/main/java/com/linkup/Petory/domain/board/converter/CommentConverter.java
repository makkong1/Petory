package com.linkup.Petory.domain.board.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.board.dto.CommentDTO;
import com.linkup.Petory.domain.board.entity.Comment;

@Component
public class CommentConverter {

    public CommentDTO toDTO(Comment comment) {
        return CommentDTO.builder()
                .idx(comment.getIdx())
                .boardId(comment.getBoard().getIdx())
                .userId(comment.getUser().getIdx())
                .username(comment.getUser().getUsername())
                .userLocation(comment.getUser().getLocation())
                .likeCount(0)
                .dislikeCount(0)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
