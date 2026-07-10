package com.linkup.Petory.domain.user.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 사용자 목록 페이징 응답 DTO (projection 기반).
 *
 * <p>JSON 형태는 기존 {@code UserPageResponseDTO}와 동일(users/totalCount/... 필드)하되,
 * 원소 타입만 경량 {@link AdminUserListDTO}로 교체한 응답이다. 공유 {@code UserPageResponseDTO}
 * (죽은 경로 포함)를 건드리지 않기 위해 분리했다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserPageResponseDTO {
    private List<AdminUserListDTO> users;
    private long totalCount;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
}
