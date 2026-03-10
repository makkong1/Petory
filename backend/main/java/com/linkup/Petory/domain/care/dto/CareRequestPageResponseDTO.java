package com.linkup.Petory.domain.care.dto;

import java.util.List;

/**
 * 펫케어 요청 페이징 응답 DTO
 */
public record CareRequestPageResponseDTO(
        List<CareRequestDTO> careRequests,
        long totalCount,
        int totalPages,
        int currentPage,
        int pageSize,
        boolean hasNext,
        boolean hasPrevious) {
}
