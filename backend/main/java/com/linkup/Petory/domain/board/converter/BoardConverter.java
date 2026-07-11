package com.linkup.Petory.domain.board.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.dto.BoardListItemDTO;
import com.linkup.Petory.domain.board.entity.Board;

import lombok.RequiredArgsConstructor;

/**
 * Board 엔티티 → BoardDTO 변환기. Lazy Loading N+1 방지를 위해 commentCount 필드만 사용한다.
 */
@Component
@RequiredArgsConstructor
public class BoardConverter {

    // Entity → DTO
    // [리팩토링] board.getComments() 접근 제거 → commentCount만 사용 (Lazy Loading N+1 방지)
    public BoardDTO toDTO(Board board) {
        Integer commentCount = board.getCommentCount() != null ? board.getCommentCount() : 0;

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
                .commentCount(commentCount)
                .likes(board.getLikeCount() != null ? board.getLikeCount() : 0)
                .dislikes(board.getDislikeCount() != null ? board.getDislikeCount() : 0)
                .views(board.getViewCount() != null ? board.getViewCount() : 0)
                .lastReactionAt(board.getLastReactionAt())
                .build();
    }

    /**
     * 목록 projection(BoardListItemDTO) → BoardDTO 기본 매핑. 리액션/첨부파일은 서비스가 배치로 사후
     * 주입하므로 여기서는 채우지 않는다(엔티티 toDTO와 동일 계약).
     */
    public BoardDTO toDTO(BoardListItemDTO item) {
        return BoardDTO.builder()
                .idx(item.getIdx())
                .title(item.getTitle())
                .content(item.getContent())
                .category(item.getCategory())
                .status(item.getStatus() != null ? item.getStatus().name() : null)
                .createdAt(item.getCreatedAt())
                .deleted(item.getIsDeleted())
                .deletedAt(item.getDeletedAt())
                .userId(item.getUserId())
                .username(item.getUsername())
                .userLocation(item.getUserLocation())
                .commentCount(item.getCommentCount() != null ? item.getCommentCount() : 0)
                .likes(item.getLikeCount() != null ? item.getLikeCount() : 0)
                .dislikes(item.getDislikeCount() != null ? item.getDislikeCount() : 0)
                .views(item.getViewCount() != null ? item.getViewCount() : 0)
                .lastReactionAt(item.getLastReactionAt())
                .build();
    }

    // DTO 리스트 변환
    public List<BoardDTO> toDTOList(List<Board> boards) {
        return boards.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
