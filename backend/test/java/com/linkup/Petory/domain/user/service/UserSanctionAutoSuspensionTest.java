package com.linkup.Petory.domain.user.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserSanction;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UserSanctionRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * 경고 누적 시 자동 이용제한이 실제로 발동하는지 검증 (2026-07-30)
 * ====================================================================================
 *
 * <p>
 * 배경: {@code UserSanctionService.addWarning} 은 경고 3회 도달 시 자동으로 이용제한을 건다. 그런데
 * 경고 횟수 증가가 {@code @Modifying} JPQL bulk UPDATE 라 <b>영속성 컨텍스트를 우회</b>한다. 같은
 * 트랜잭션 안에서 임계값을 검사하려고 사용자를 다시 조회하면 1차 캐시가 <b>증가 이전의 낡은 값</b>을 돌려주고,
 * 그래서 {@code warningCount >= 3} 이 성립하지 않아 <b>이용제한이 영영 걸리지 않았다.</b>
 *
 * <p>
 * 기존 동시성 테스트는 이 결함을 못 잡았다 — 단언이
 * {@code if (status == SUSPENDED) { ... }} 형태의 <b>조건부</b>라, 이용제한이 안 걸리면 검사 자체가
 * 통째로 건너뛰어지고 초록불이 났기 때문이다. 그래서 이 테스트는 <b>무조건</b> 단언한다.
 *
 * <p>
 * 각 {@code addWarning} 호출은 독립 트랜잭션이므로(@Transactional 메서드, 테스트는 트랜잭션 없음)
 * 실제 운영 흐름과 같다.
 * ====================================================================================
 */
@SpringBootTest
class UserSanctionAutoSuspensionTest {

    @Autowired
    private UserSanctionService userSanctionService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private UserSanctionRepository sanctionRepository;

    /** UserSanctionService.WARNING_THRESHOLD 와 같아야 한다. */
    private static final int WARNING_THRESHOLD = 3;

    private Users target;
    private Users admin;

    @BeforeEach
    void setUp() {
        long stamp = System.nanoTime();
        target = usersRepository.save(Users.builder()
                .id("warn_target_" + stamp)
                .username("warn_target_" + stamp)
                .email("warn_target_" + stamp + "@test.petory")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .warningCount(0)
                .build());

        admin = usersRepository.save(Users.builder()
                .id("warn_admin_" + stamp)
                .username("warn_admin_" + stamp)
                .email("warn_admin_" + stamp + "@test.petory")
                .password("password")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .warningCount(0)
                .build());
    }

    @Test
    @DisplayName("경고 3회를 채우면 자동 이용제한이 걸린다 (bulk UPDATE 후 1차 캐시 낡음 회귀 방지)")
    void 경고가_임계값에_도달하면_자동_이용제한이_적용된다() {
        for (int i = 1; i <= WARNING_THRESHOLD; i++) {
            userSanctionService.addWarning(target.getIdx(), "테스트 경고 " + i, admin.getIdx(), null);
        }

        Users after = usersRepository.findById(target.getIdx()).orElseThrow();

        assertThat(after.getWarningCount())
                .as("경고 %d회를 부여했으므로 warningCount 는 %d 이어야 한다", WARNING_THRESHOLD, WARNING_THRESHOLD)
                .isEqualTo(WARNING_THRESHOLD);

        assertThat(after.getStatus())
                .as("경고 %d회 누적이면 자동 이용제한이 걸려야 한다. ACTIVE 로 남아 있다면 "
                        + "incrementWarningCount(bulk UPDATE)가 영속성 컨텍스트를 우회해 "
                        + "임계값 검사가 1차 캐시의 낡은 warningCount 를 읽은 것이다.", WARNING_THRESHOLD)
                .isEqualTo(UserStatus.SUSPENDED);

        assertThat(after.getSuspendedUntil())
                .as("이용제한에는 해제 시각이 있어야 한다")
                .isNotNull();

        List<UserSanction> sanctions = sanctionRepository.findByUserOrderByCreatedAtDesc(after);
        assertThat(sanctions)
                .filteredOn(s -> s.getSanctionType() == UserSanction.SanctionType.SUSPENSION)
                .as("자동 이용제한 기록(SUSPENSION)이 남아야 한다")
                .hasSize(1);
    }

    @Test
    @DisplayName("임계값 직전(2회)에는 이용제한이 걸리지 않는다 (경계값)")
    void 임계값_직전에는_이용제한이_걸리지_않는다() {
        for (int i = 1; i < WARNING_THRESHOLD; i++) {
            userSanctionService.addWarning(target.getIdx(), "테스트 경고 " + i, admin.getIdx(), null);
        }

        Users after = usersRepository.findById(target.getIdx()).orElseThrow();

        assertThat(after.getWarningCount()).isEqualTo(WARNING_THRESHOLD - 1);
        assertThat(after.getStatus())
                .as("경고 %d회로는 아직 이용제한 대상이 아니다", WARNING_THRESHOLD - 1)
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(after.getSuspendedUntil()).isNull();
    }
}
