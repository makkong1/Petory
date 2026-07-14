package com.linkup.Petory.global.performance;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.admin.service.AdminCareAndMeetupFacade;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;
import com.linkup.Petory.domain.user.entity.PetVaccination;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.PetRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.user.service.PetService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * 쿼리 수가 결과 건수에 비례하지 않는지 검증 (N+1 회귀 방지, 2026-07-14)
 * ====================================================================================
 *
 * 왜 "쿼리 수가 줄었다" 가 아니라 "비례하지 않는다" 를 검증하는가?
 *
 *   N+1 의 정의는 "쿼리 수가 결과 건수에 비례한다" 이다.
 *   쿼리 수 상한만 걸어두면(예: "20개 미만") 결과가 10건일 때는 통과하고
 *   100건일 때만 터지는 테스트가 된다 — 즉 헛돈다.
 *
 *   그래서 같은 엔드포인트를 size 를 바꿔 두 번 호출하고, 쿼리 수 증가분을 본다.
 *   - N+1 이면: size 를 4배로 늘리면 쿼리도 대략 4배가 된다.
 *   - 배치/fetch join 이 정상이면: size 와 무관하게 거의 일정하다.
 *
 * 실측 근거 (docs/analysis/query-audit/fixes-2026-07-14.md):
 *   admin care 수정 전  size 10/20/40 → 쿼리 36 / 66 / 127   (비례 = N+1)
 *   admin care 수정 후  size 10/20/40 → 쿼리  7 /  7 /   8   (비례하지 않음)
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class QueryCountScalingRegressionTest {

    @Autowired
    private AdminCareAndMeetupFacade adminFacade;

    @Autowired
    private PetService petService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private CareRequestRepository careRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int SMALL = 5;
    private static final int LARGE = 40;

    /** size 를 8배로 늘렸을 때 허용하는 추가 SQL 문 수. 배치/fetch join 이면 0~2개면 충분하다. */
    private static final long ALLOWED_EXTRA_QUERIES = 3;

    @BeforeEach
    void setUp() {
        Users owner = usersRepository.save(Users.builder()
                .id("scaling_owner")
                .username("scaling_owner")
                .email("scaling_owner@test.com")
                .nickname("스케일링검증")
                .password("password")
                .role(Role.USER)
                .build());

        // LARGE 보다 넉넉히 만들어야 size=40 이 실제로 40건을 채운다
        for (int i = 0; i < LARGE + 10; i++) {
            Pet pet = petRepository.save(Pet.builder()
                    .user(owner)
                    .petName("펫" + i)
                    .petType(PetType.DOG)
                    .isNeutered(false)
                    .build());

            // 지연로딩 대상들 — N+1 이 있으면 여기서 행마다 쿼리가 나간다
            entityManager.persist(PetVaccination.builder()
                    .pet(pet)
                    .vaccineName("종합백신")
                    .vaccinatedAt(LocalDate.now().minusMonths(6))
                    .isDeleted(false)
                    .build());
            entityManager.persist(AttachmentFile.builder()
                    .targetType(FileTargetType.PET)
                    .targetIdx(pet.getIdx())
                    .filePath("pet_" + i + ".jpg")
                    .fileType("image/jpeg")
                    .build());

            careRequestRepository.save(CareRequest.builder()
                    .user(owner)
                    .pet(pet)
                    .title("케어 요청 " + i)
                    .description("설명 " + i)
                    .date(LocalDateTime.now().plusDays(1))
                    .status(CareRequestStatus.OPEN)
                    .isDeleted(false)
                    .build());
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("admin 케어요청 목록: 쿼리 수가 결과 건수에 비례하지 않는다 (toDTOList 배치 회귀 방지)")
    void adminCareRequestListDoesNotScaleWithSize() {
        long small = countQueries(() -> adminFacade.getCareRequests(null, null, null, 0, SMALL));
        long large = countQueries(() -> adminFacade.getCareRequests(null, null, null, 0, LARGE));

        System.out.println("[admin care] size " + SMALL + " → 쿼리 " + small
                + " / size " + LARGE + " → 쿼리 " + large);

        assertThat(large - small)
                .as("size 를 %d → %d 로 늘렸는데 SQL 문이 %d개 늘었다. 원인은 둘 중 하나다: "
                        + "(1) findAllForAdmin 의 JOIN FETCH 가 빠져 pet·user 가 행마다 지연로딩되거나, "
                        + "(2) AdminCareAndMeetupFacade 가 toDTOList(배치) 대신 "
                        + "Page.map(careRequestConverter::toDTO)(단건)로 되돌아가 행마다 첨부를 조회한다.",
                        SMALL, LARGE, large - small)
                .isLessThanOrEqualTo(ALLOWED_EXTRA_QUERIES);
    }

    @Test
    @DisplayName("펫 타입별 목록: 쿼리 수가 결과 건수에 비례하지 않는다 (toDTOList 배치 회귀 방지)")
    void petsByTypeDoesNotScaleWithSize() {
        long small = countQueries(() -> petService.getPetsByType("DOG", PageRequest.of(0, SMALL)));
        long large = countQueries(() -> petService.getPetsByType("DOG", PageRequest.of(0, LARGE)));

        System.out.println("[pets] size " + SMALL + " → 쿼리 " + small
                + " / size " + LARGE + " → 쿼리 " + large);

        assertThat(large - small)
                .as("size 를 %d → %d 로 늘렸는데 쿼리가 %d개 늘었다. "
                        + "PetService 가 toDTOList(배치) 대신 Page.map(petConverter::toDTO)(단건)로 "
                        + "되돌아간 것이다 — 단건 toDTO 는 펫마다 첨부를 조회한다.",
                        SMALL, LARGE, large - small)
                .isLessThanOrEqualTo(ALLOWED_EXTRA_QUERIES);
    }

    @Test
    @DisplayName("펫 타입별 목록: 페이징이 살아 있다 (요청한 size 만큼만 반환한다)")
    void petsByTypeRespectsPageSize() {
        var page = petService.getPetsByType("DOG", PageRequest.of(0, SMALL));

        assertThat(page.getContent())
                .as("페이징이 빠지면 DOG 전체(운영 기준 7,667건)가 한 번에 반환된다")
                .hasSize(SMALL);
        assertThat(page.getTotalElements())
                .as("총건수는 페이지 크기와 무관하게 전체를 세야 한다")
                .isGreaterThanOrEqualTo(LARGE + 10);
    }

    private long countQueries(Runnable action) {
        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        action.run();

        // ⚠️ getQueryExecutionCount() 를 쓰면 안 된다 — 지연로딩 엔티티 로드(PK 단건 조회)를 세지 않는다.
        //    실제로 이 테스트는 처음에 그 지표를 썼고, JOIN FETCH 를 제거해도 통과해버렸다(눈먼 테스트).
        //    getPrepareStatementCount() 는 JDBC 로 나간 문(statement) 전부를 센다.
        return stats.getPrepareStatementCount();
    }
}
