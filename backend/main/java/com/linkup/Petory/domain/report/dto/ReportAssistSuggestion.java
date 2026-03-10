package com.linkup.Petory.domain.report.dto;

import com.linkup.Petory.domain.report.entity.ReportActionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 신고 보조 에이전트가 제안하는 내용 (관리자 참고용, 자동 처리 아님)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportAssistSuggestion {

    /** 신고 내용 한 줄 요약 */
    private String summary;

    /** 심각도 제안: LOW, MEDIUM, HIGH */
    private String suggestedSeverity;

    /** 조치 유형 제안 (ReportActionType과 매핑) */
    private ReportActionType suggestedAction;

    /** 제안 이유 (1~2문장) */
    private String reasoning;
}
