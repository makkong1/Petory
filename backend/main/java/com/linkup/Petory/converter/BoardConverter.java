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

    // Entity → DTO
    public BoardDTO toDTO(Board board) {
        return BoardDTO.builder()
                .idx(board.getIdx())
                .title(board.getTitle())
                .content(board.getContent())
                .category(board.getCategory())
                .createdAt(board.getCreatedAt())
                .userId(board.getUser().getIdx())
                .username(board.getUser().getUsername())
                .userLocation(board.getUser().getLocation())
                .commentCount(board.getComments() != null ? board.getComments().size() : 0)
                .boardFilePath(board.getBoardFilePath())
                .commentFilePath(board.getCommentFilePath())
                .likes(0) // 추후 구현
                .views(0) // 추후 구현
                .build();
    }

    // DTO 리스트 변환
    public List<BoardDTO> toDTOList(List<Board> boards) {
        return boards.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}