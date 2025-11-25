package com.linkup.Petory.domain.board.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.entity.Board;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoardConverter {

    // Entity → DTO
    public BoardDTO toDTO(Board board) {
        Integer aggregatedCommentCount = board.getCommentCount();
        if (aggregatedCommentCount == null && board.getComments() != null) {
            aggregatedCommentCount = board.getComments().size();
        }

        return BoardDTO.builder()
                .idx(board.getIdx())
                .title(board.getTitle())
                .content(board.getContent())
                .category(board.getCategory())
                .status(board.getStatus() != null ? board.getStatus().name() : null)
                .createdAt(board.getCreatedAt())
                .deleted(board.getIsDeleted())
                .deletedAt(board.getDeletedAt())
                .userId(board.getUser() != null ? board.getUser().getIdx() : null)
                .username(board.getUser() != null ? board.getUser().getUsername() : null)
                .userLocation(board.getUser() != null ? board.getUser().getLocation() : null)
                .commentCount(aggregatedCommentCount != null ? aggregatedCommentCount : 0)
                .likes(board.getLikeCount() != null ? board.getLikeCount() : 0)
                .dislikes(0)
                .views(board.getViewCount() != null ? board.getViewCount() : 0)
                .lastReactionAt(board.getLastReactionAt())
                .build();
    }

    // DTO 리스트 변환
    public List<BoardDTO> toDTOList(List<Board> boards) {
        return boards.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}