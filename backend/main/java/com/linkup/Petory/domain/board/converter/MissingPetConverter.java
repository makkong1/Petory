package com.linkup.Petory.domain.board.converter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.board.dto.MissingPetBoardDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentDTO;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;

@Component
public class MissingPetConverter {

    public MissingPetBoardDTO toBoardDTO(MissingPetBoard board) {
        List<MissingPetCommentDTO> commentDTOs = board.getComments() == null
                ? Collections.emptyList()
                : board.getComments().stream()
                        .map(this::toCommentDTO)
                        .collect(Collectors.toList());

        return MissingPetBoardDTO.builder()
                .idx(board.getIdx())
                .userId(board.getUser().getIdx())
                .username(board.getUser().getUsername())
                .phoneNumber(board.getUser() != null ? board.getUser().getPhone() : null)
                .title(board.getTitle())
                .content(board.getContent())
                .petName(board.getPetName())
                .species(board.getSpecies())
                .breed(board.getBreed())
                .gender(board.getGender())
                .age(board.getAge())
                .color(board.getColor())
                .lostDate(board.getLostDate())
                .lostLocation(board.getLostLocation())
                .latitude(board.getLatitude())
                .longitude(board.getLongitude())
                .status(board.getStatus())
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .comments(commentDTOs)
                .commentCount(commentDTOs.size())
                .build();
    }

    public List<MissingPetBoardDTO> toBoardDTOList(List<MissingPetBoard> boards) {
        return boards.stream()
                .map(this::toBoardDTO)
                .collect(Collectors.toList());
    }

    public MissingPetCommentDTO toCommentDTO(MissingPetComment comment) {
        return MissingPetCommentDTO.builder()
                .idx(comment.getIdx())
                .boardId(comment.getBoard().getIdx())
                .userId(comment.getUser().getIdx())
                .username(comment.getUser().getUsername())
                .content(comment.getContent())
                .address(comment.getAddress())
                .latitude(comment.getLatitude())
                .longitude(comment.getLongitude())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    public List<MissingPetCommentDTO> toCommentDTOList(List<MissingPetComment> comments) {
        return comments.stream()
                .map(this::toCommentDTO)
                .collect(Collectors.toList());
    }
}
