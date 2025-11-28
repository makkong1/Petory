package com.linkup.Petory.domain.activity.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPageResponseDTO {
    private List<ActivityDTO> activities;
    private long totalCount;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
    
    // 필터별 개수 (필터 버튼에 표시용)
    private long allCount;
    private long postsCount;
    private long commentsCount;
    private long reviewsCount;
}

