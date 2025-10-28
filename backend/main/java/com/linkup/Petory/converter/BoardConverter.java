package com.linkup.Petory.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.dto.BoardDTO;
import com.linkup.Petory.entity.Board;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoardConverter {

    private final CommentConverter commentConverter;

    public BoardDTO toDTO(Board board) {
        return BoardDTO.builder()
                .idx(board.getIdx())
                .userId(board.getUser().getIdx())
                .username(board.getUser().getUsername())
                .title(board.getTitle())
                .content(board.getContent())
                .category(board.getCategory())
                .createdAt(board.getCreatedAt())
                .comments(board.getComments() != null ? board.getComments().stream()
                        .map(commentConverter::toDTO)
                        .collect(Collectors.toList())
                        : null)
                .build();
    }

    public List<BoardDTO> toDTOList(List<Board> boards) {
        return boards.stream().map(this::toDTO).collect(Collectors.toList());
    }
}
