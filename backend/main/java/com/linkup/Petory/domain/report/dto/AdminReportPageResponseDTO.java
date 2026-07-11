package com.linkup.Petory.domain.report.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 신고 목록 페이징 응답 DTO (projection 기반).
 *
 * <p>기존 전건 반환(List) 대신 DB 페이징 결과를 감싼다. 프론트는 {@code reports}(현재 페이지)와
 * {@code totalCount}로 페이지네이션을 구성한다. AdminUserPageResponseDTO와 동일한 형태.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportPageResponseDTO {
    private List<ReportDTO> reports;
    private long totalCount;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
}
