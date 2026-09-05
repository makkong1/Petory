package com.linkup.Petory.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * V17 회귀 테스트 — report.target_type DB enum에 CARE_REVIEW 반영 확인.
 *
 * ReportTargetType(Java)·ReportService.validateTarget()엔 CARE_REVIEW가 있었지만
 * V1 baseline의 DB enum엔 없어서, care_review 신고 INSERT가
 * "Data truncated for column 'target_type'"로 실패했다 (Mockito 단위 테스트는 실제 DB
 * enum 제약을 거치지 않아 이 버그를 못 잡는다 — 그래서 실제 DB에 flush하는 통합 테스트로 검증).
 *
 * 근거: backend/main/resources/db/migration/V17__report_target_type_add_care_review.sql
 */
@SpringBootTest
@Transactional
class ReportTargetTypeEnumTest {

    @Autowired
    private EntityManager em;

    @Test
    void careReviewTargetTypePersistsWithoutTruncation() {
        String marker = "rtt_" + UUID.randomUUID().toString().substring(0, 8);
        Users reporter = Users.builder()
                .id(marker)
                .username(marker)
                .email(marker + "@test.petory")
                .password("x")
                .role(Role.USER)
                .build();
        em.persist(reporter);

        Report report = Report.builder()
                .targetType(ReportTargetType.CARE_REVIEW)
                .targetIdx(1L)
                .reporter(reporter)
                .reason("테스트 신고")
                .build();
        em.persist(report);
        em.flush();

        assertThat(report.getIdx()).isNotNull();
    }
}
