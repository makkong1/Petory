package com.linkup.Petory.domain.report.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReportDetailDTO {
    ReportDTO report;
    TargetPreview target;

    @Value
    @Builder
    public static class TargetPreview {
        String type;       // BOARD / COMMENT / MISSING_PET / PET_CARE_PROVIDER
        Long id;           // target idx
        String title;      // 게시글/실종제보 제목 등
        String summary;    // 내용 요약
        String authorName; // 작성자/대상자 이름
    }
}


