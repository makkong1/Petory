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

    /**
     * 게시글 DTO 변환 (댓글 포함)
     * 댓글이 이미 로드된 경우에만 사용 (N+1 문제 주의)
     */
    public MissingPetBoardDTO toBoardDTO(MissingPetBoard board) {
        List<MissingPetCommentDTO> commentDTOs = board.getComments() == null
                ? Collections.emptyList()
                : board.getComments().stream()
                        .filter(comment -> !comment.getIsDeleted()) // 삭제된 댓글 제외
                        .sorted((c1, c2) -> {
                            // createdAt 기준 오름차순 정렬 (최신순)
                            if (c1.getCreatedAt() == null && c2.getCreatedAt() == null)
                                return 0;
                            if (c1.getCreatedAt() == null)
                                return 1;
                            if (c2.getCreatedAt() == null)
                                return -1;
                            return c1.getCreatedAt().compareTo(c2.getCreatedAt());
                        })
                        .map(this::toCommentDTO)
                        .collect(Collectors.toList());

        return MissingPetBoardDTO.builder()
                .idx(board.getIdx())
                .userId(board.getUser().getIdx())
                .username(board.getUser().getUsername())
                .nickname(board.getUser().getNickname())
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
                .deleted(board.getIsDeleted())
                .deletedAt(board.getDeletedAt())
                .comments(commentDTOs)
                .commentCount(commentDTOs.size())
                .build();
    }

    /**
     * 게시글 DTO 변환 (댓글 제외, N+1 문제 방지)
     * 목록 조회 시 사용 - 댓글을 접근하지 않아 lazy loading을 트리거하지 않음
     */
    public MissingPetBoardDTO toBoardDTOWithoutComments(MissingPetBoard board) {
        return MissingPetBoardDTO.builder()
                .idx(board.getIdx())
                .userId(board.getUser().getIdx())
                .username(board.getUser().getUsername())
                .nickname(board.getUser().getNickname())
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
                .deleted(board.getIsDeleted())
                .deletedAt(board.getDeletedAt())
                .comments(Collections.emptyList()) // 댓글은 빈 리스트
                .commentCount(0) // 댓글 수는 0
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
                .nickname(comment.getUser().getNickname())
                .content(comment.getContent())
                .address(comment.getAddress())
                .latitude(comment.getLatitude())
                .longitude(comment.getLongitude())
                .createdAt(comment.getCreatedAt())
                .status(comment.getIsDeleted() ? "DELETED" : "ACTIVE")
                .deleted(comment.getIsDeleted())
                .deletedAt(comment.getDeletedAt())
                .build();
    }

    public List<MissingPetCommentDTO> toCommentDTOList(List<MissingPetComment> comments) {
        return comments.stream()
                .map(this::toCommentDTO)
                .collect(Collectors.toList());
    }
}
