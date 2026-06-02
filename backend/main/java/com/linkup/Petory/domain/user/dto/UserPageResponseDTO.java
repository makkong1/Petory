package com.linkup.Petory.domain.user.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 관리자용 사용자 목록 페이징 응답 DTO. 전체 건수·페이지 메타를 포함한다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPageResponseDTO {
    private List<UsersDTO> users;
    private long totalCount;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
}

