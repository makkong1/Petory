package com.linkup.Petory.domain.board.dto;

import java.util.List;

/**
 * 실종동물 댓글 페이징 응답 DTO
 * <p>
 * record: 불변 데이터 캐리어. Jackson 직렬화, 페이징 메타 정보 전달용.
 * </p>
 */
public record MissingPetCommentPageResponseDTO(
                List<MissingPetCommentDTO> comments,
                long totalCount,
                int totalPages,
                int currentPage,
                int pageSize,
                boolean hasNext,
                boolean hasPrevious) {
}
