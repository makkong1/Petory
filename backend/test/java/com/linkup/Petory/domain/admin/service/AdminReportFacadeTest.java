package com.linkup.Petory.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.entity.ReportActionType;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.service.ReportService;

@ExtendWith(MockitoExtension.class)
class AdminReportFacadeTest {

    @InjectMocks
    private AdminReportFacade facade;

    @Mock
    private ReportService reportService;
    @Mock
    private AdminAuditService auditService;

    @Test
    @DisplayName("정상: 신고 처리 시 감사 로그를 남긴다")
    void 정상_신고처리_감사로그() {
        ReportHandleRequest request = new ReportHandleRequest();
        request.setStatus(ReportStatus.RESOLVED);
        request.setActionTaken(ReportActionType.WARN_USER);
        ReportDTO dto = ReportDTO.builder().idx(5L).build();
        when(reportService.handleReport(5L, 9L, request)).thenReturn(dto);

        ReportDTO result = facade.handleReport(5L, request, 9L);

        assertThat(result).isSameAs(dto);
        verify(auditService).log(9L, "REPORT_HANDLE", "REPORT", 5L, "status=RESOLVED,action=WARN_USER");
    }
}
