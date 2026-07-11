package com.linkup.Petory.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.report.dto.AdminReportPageResponseDTO;
import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * [오버페칭 제거] 신고 목록 projection 페이징 검증.
 *
 * reporter/handledBy를 통째로 로딩하던 것을 idx/username 2컬럼만 SELECT하는 생성자 표현식 projection +
 * DB 페이징으로 전환한 뒤에도 (1) JPQL(생성자 표현식·LEFT JOIN·CAST(NULL AS integer))이 런타임에 유효하고,
 * (2) 화면이 쓰는 필드(reporterId/reporterName·status·targetType 등)가 정확히 매핑되며 페이징 메타가 맞는지 확인한다.
 */
@SpringBootTest
@Transactional
class ReportListProjectionTest {

    @Autowired
    private ReportService reportService;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private UsersRepository usersRepository;
    @PersistenceContext
    private EntityManager em;

    private String tag;
    private Users reporter;
    private Report report;

    @BeforeEach
    void setUp() {
        tag = "rpt" + UUID.randomUUID().toString().substring(0, 8);

        reporter = usersRepository.save(Users.builder()
                .id(tag + "-reporter")
                .username(tag + "-신고자")
                .email(tag + "-reporter@test.com")
                .nickname(tag + "-닉")
                .password("password")
                .role(Role.USER)
                .build());

        report = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(ReportTargetType.BOARD)
                .targetIdx(999_000L)
                .reason(tag + " 부적절한 게시글")
                .status(ReportStatus.PENDING)
                .build());

        em.flush();
    }

    @Test
    @DisplayName("정상: projection 필드(신고자·대상·상태)가 매핑되고 미처리 handledBy는 null(LEFT JOIN), reportCount는 null(CAST)")
    void 정상_신고목록_projection_필드매핑() {
        AdminReportPageResponseDTO res = reportService.getReports(
                ReportTargetType.BOARD, ReportStatus.PENDING, PageRequest.of(0, 20));

        ReportDTO dto = res.getReports().stream()
                .filter(x -> x.getIdx().equals(report.getIdx()))
                .findFirst().orElseThrow();

        assertThat(dto.getTargetType()).isEqualTo(ReportTargetType.BOARD);
        assertThat(dto.getTargetIdx()).isEqualTo(999_000L);
        assertThat(dto.getReporterId()).isEqualTo(reporter.getIdx());   // JOIN reporter
        assertThat(dto.getReporterName()).isEqualTo(tag + "-신고자");
        assertThat(dto.getReason()).isEqualTo(tag + " 부적절한 게시글");
        assertThat(dto.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(dto.getHandledBy()).isNull();                        // LEFT JOIN (미처리)
        assertThat(dto.getHandledByName()).isNull();
        assertThat(dto.getReportCount()).isNull();                      // CAST(NULL AS integer)
        assertThat(res.getTotalCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("경계: 상태 필터(RESOLVED)로는 PENDING 신고가 조회되지 않는다")
    void 경계_상태필터_불일치는_제외() {
        AdminReportPageResponseDTO res = reportService.getReports(
                ReportTargetType.BOARD, ReportStatus.RESOLVED, PageRequest.of(0, 20));

        assertThat(res.getReports()).extracting(ReportDTO::getIdx)
                .doesNotContain(report.getIdx());
    }
}
