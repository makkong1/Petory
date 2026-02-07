package com.linkup.Petory.domain.board.dto;

import java.util.List;

/**
 * 게시글 페이징 응답 DTO
 * <p>
 * record: 불변 데이터 캐리어. Jackson 직렬화, 페이징 메타 정보 전달용.
 * </p>
 */
public record BoardPageResponseDTO(
        List<BoardDTO> boards,
        long totalCount,
        int totalPages,
        int currentPage,
        int pageSize,
        boolean hasNext,
        boolean hasPrevious) {
}
