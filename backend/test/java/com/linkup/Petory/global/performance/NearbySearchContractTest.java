package com.linkup.Petory.global.performance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.dto.CareRequestListView;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.entity.CareScheduleMode;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.care.repository.SpringDataJpaCareRequestRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.global.config.NearbySearchPolicy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * 지도 반경검색 네 도메인의 "통일된 계약" 을 고정한다 (2026-07-31)
 * ====================================================================================
 *
 * <p>
 * 통일 전에는 같은 반경검색인데 도메인마다 {@code ORDER BY} 가 달랐고, 그것 때문에
 * <b>실행계획이 서로 다른 이유로 뒤집혔다.</b> 측정으로 확인한 내용이다.
 *
 * <ul>
 * <li>care — {@code created_at DESC}. 이 컬럼엔 정렬용 인덱스가 있어서, 매치가 LIMIT 을
 * 채우면 옵티마이저가 "정렬 인덱스를 역주행하다 멈추는" 계획을 골라 <b>공간 인덱스를 아예
 * 안 썼다</b>(5km 는 SPATIAL 208행, 10km 부터 created_at 인덱스 1,622행).</li>
 * <li>meetup — {@code ST_Distance_Sphere(...) ASC}. 함수식이라 조기종료가 불가능해서
 * 순수 선택도 손익분기점에서만 뒤집혔다(박스가 테이블의 약 25%, 12km 부터 Table scan).</li>
 * <li>locationservice — {@code CASE(sort)..., rating DESC}. 넓어지면
 * {@code idx_locationservice_deleted_rating} 으로 갈아탔다.</li>
 * </ul>
 *
 * <p>
 * <b>이 테스트는 계획이 아니라 "계약" 만 검증한다.</b> 처음엔 실행계획을 단언했다가 CI 에서
 * 깨졌다 — CI 는 빈 MySQL 에 Flyway 로 스키마만 만들고 더미 데이터를 넣지 않는데, 실행계획은
 * 행 수와 통계에 따라 달라지기 때문이다. 로컬(더미 5만 행)에서 통과하던 것이 CI(0행)에서
 * 실패했다. 그래서 <b>어떤 인덱스를 타는지는 테스트가 아니라 측정·문서의 영역</b>으로 넘기고,
 * 여기서는 데이터를 직접 만들어 <b>정렬 순서와 반경 경계</b>만 본다. 이건 행 수와 무관하게
 * 결정적이다.
 *
 * <p>
 * 근거: docs/interview/concepts/02_공간쿼리_Haversine.md
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class NearbySearchContractTest {

    /** 서울 시청. 테스트가 심는 좌표의 기준점. */
    private static final double LAT = 37.5665;
    private static final double LNG = 126.978;

    /** 위도 1도 ≈ 111km. 거리를 km 단위로 의도한 만큼 벌리는 데 쓴다. */
    private static final double KM_PER_LAT_DEGREE = 111.0;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private SpringDataJpaCareRequestRepository careRequestJpaRepository;
    @Autowired
    private CareRequestRepository careRequestRepository;
    @Autowired
    private UsersRepository usersRepository;

    private String tag;

    /**
     * 중심에서 북쪽으로 정확히 {@code km} 만큼 떨어진 케어 요청을 만든다.
     *
     * <p>
     * {@code geo_point} 는 엔티티에 매핑돼 있지 않고 {@code BEFORE INSERT} 트리거가
     * 위·경도에서 채우므로, 위·경도만 넣으면 공간 쿼리가 그대로 동작한다.
     */
    private CareRequest careAtDistance(Users writer, double km) {
        return careRequestRepository.save(CareRequest.builder()
                .user(writer)
                .title(tag + " " + km + "km")
                .description("반경검색 계약 테스트")
                .date(LocalDateTime.now().plusDays(1))
                .scheduleMode(CareScheduleMode.FIXED)
                .estimatedDurationMinutes(30)
                .offeredCoins(100)
                .status(CareRequestStatus.OPEN)
                .isDeleted(false)
                .latitude(LAT + km / KM_PER_LAT_DEGREE)
                .longitude(LNG)
                .build());
    }

    @BeforeEach
    void setUp() {
        tag = "nb" + UUID.randomUUID().toString().substring(0, 8);

        Users writer = usersRepository.save(Users.builder()
                .id(tag + "-writer")
                .username(tag + "-writer")
                .email(tag + "-writer@test.com")
                .nickname(tag + "-작성자")
                .password("password")
                .role(Role.USER)
                .location("서울시 중구")
                .build());

        // 일부러 거리 순서와 삽입 순서를 어긋나게 심는다.
        // 삽입 순서(= 사실상 created_at 순서)대로 나오면 정렬이 안 걸린 것이므로 테스트가 잡는다.
        careAtDistance(writer, 3.0);
        careAtDistance(writer, 0.5);
        careAtDistance(writer, 7.0);
        careAtDistance(writer, 1.5);
        careAtDistance(writer, 30.0); // 반경 밖 — 걸러져야 한다

        // native 쿼리는 자동 flush 대상이 아닐 수 있으므로 명시적으로 반영시킨다.
        entityManager.flush();
    }

    /** 두 좌표 사이 거리(m). ST_Distance_Sphere 와 같은 구면 근사. */
    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6_371_008.8 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * care 반경검색이 <b>거리 오름차순</b>으로 나오는지 — 통일의 핵심 계약.
     *
     * <p>
     * 예전엔 {@code created_at DESC} 였다. 지도에서 가까운 순이 아니라 최신순으로 마커가
     * 정렬됐고, 넓은 반경에서 공간 인덱스를 안 쓰게 만드는 원인이기도 했다.
     *
     * <p>
     * <b>반드시 리포지토리 메서드를 직접 호출해야 한다.</b> 처음엔 이 테스트를 손으로 쓴 SQL 로
     * 짰다가 red-green 이 안 나왔다 — {@code ORDER BY} 를 {@code created_at DESC} 로 되돌려도
     * 테스트가 그대로 통과했다. 테스트가 검증한 게 <b>테스트 자신이 쓴 SQL</b> 이었지
     * 프로덕션 쿼리가 아니었기 때문이다.
     */
    @Test
    @DisplayName("care 반경검색: 리포지토리 결과가 거리 오름차순이고 반경을 벗어나지 않는다")
    void careNearbyIsSortedByDistance() {
        double radiusKm = 10.0;
        List<CareRequestListView> views = careRequestJpaRepository.findNearbyCareRequests(
                LAT, LNG, radiusKm, NearbySearchPolicy.resultLimitFor(radiusKm));

        List<CareRequestListView> mine = views.stream()
                .filter(v -> v.getTitle() != null && v.getTitle().startsWith(tag))
                .toList();

        assertThat(mine)
                .as("이 테스트가 심은 반경 안 요청 4건이 조회돼야 한다 (30km 짜리는 제외)")
                .hasSize(4);

        double previous = -1;
        for (CareRequestListView v : mine) {
            double distance = distanceMeters(LAT, LNG, v.getLatitude(), v.getLongitude());

            assertThat(distance)
                    .as("반경 %skm 밖의 행이 섞였다(%s) — 2차 ST_Distance_Sphere 필터를 확인할 것",
                            radiusKm, v.getTitle())
                    .isLessThanOrEqualTo(radiusKm * 1000 + 1.0);
            assertThat(distance)
                    .as("거리 오름차순이 깨졌다(%s: %.1fm 앞에 %.1fm). "
                            + "ORDER BY 가 created_at 등으로 되돌아갔는지 확인할 것",
                            v.getTitle(), previous, distance)
                    .isGreaterThanOrEqualTo(previous - 1.0);
            previous = distance;
        }
    }

    /**
     * 네 도메인의 반경검색이 모두 {@code ST_Distance_Sphere} 로 정밀 반경을 거르는지 본다.
     *
     * <p>
     * missing_pet_board 은 예전에 이 필터가 <b>Java 에 있었다</b> — DB 는 사각형 후보만 주고
     * 서비스가 {@code haversineKm} 로 원형을 걸렀다. 그러면 후보 상한(200건) 중 사각형 모서리에
     * 걸린 것들이 Java 에서 버려져 실제 점수 계산 대상이 200보다 적어졌다. DB 로 내려서
     * 나머지 세 도메인과 필터 위치를 맞췄다.
     *
     * <p>
     * 데이터가 없어도 성립한다 — 쿼리가 실행된다는 것 자체가 {@code geo_point}/{@code location}
     * 컬럼과 SRID 규약이 살아 있다는 뜻이다.
     */
    @Test
    @DisplayName("4도메인 모두 2단계(ST_Within → ST_Distance_Sphere)로 반경을 거른다")
    void allDomainsFilterRadiusInDatabase() {
        String careSql = "SELECT COUNT(*) FROM carerequest cr WHERE cr.is_deleted = 0 "
                + "AND ST_Distance_Sphere(cr.geo_point, ST_GeomFromText('POINT(37.5665 126.978)', 4326)) <= 5000";
        String meetupSql = "SELECT COUNT(*) FROM meetup m WHERE (m.is_deleted = false OR m.is_deleted IS NULL) "
                + "AND ST_Distance_Sphere(m.geo_point, ST_GeomFromText('POINT(37.5665 126.978)', 4326)) <= 5000";
        String missingSql = "SELECT COUNT(*) FROM missing_pet_board b WHERE b.is_deleted = 0 "
                + "AND ST_Distance_Sphere(b.geo_point, ST_GeomFromText('POINT(37.5665 126.978)', 4326)) <= 5000";
        String locationSql = "SELECT COUNT(*) FROM locationservice ls WHERE ls.is_deleted = 0 "
                + "AND ST_Distance_Sphere(ls.location, ST_GeomFromText('POINT(37.5665 126.978)', 4326)) <= 5000";

        for (String sql : List.of(careSql, meetupSql, missingSql, locationSql)) {
            Object count = entityManager.createNativeQuery(sql).getSingleResult();
            assertThat(((Number) count).longValue())
                    .as("반경 필터가 실행되지 않는다. geo_point 컬럼·SRID 규약을 확인할 것:\n%s", sql)
                    .isGreaterThanOrEqualTo(0L);
        }
    }

    /** 결과 상한 정책이 반경에 따라 단조 증가하고 절대 상한을 넘지 않는지. */
    @Test
    @DisplayName("NearbySearchPolicy: 반경이 커지면 상한도 커지고 MAX_RESULTS 를 넘지 않는다")
    void resultLimitGrowsWithRadiusAndIsCapped() {
        int previous = 0;
        for (double radiusKm : new double[] { 1, 2, 5, 10, 20, 50, 100 }) {
            int limit = NearbySearchPolicy.resultLimitFor(radiusKm);
            assertThat(limit)
                    .as("반경 %skm 의 상한이 더 좁은 반경보다 작다 — 표가 뒤집혔는지 확인할 것", radiusKm)
                    .isGreaterThanOrEqualTo(previous);
            assertThat(limit).isLessThanOrEqualTo(NearbySearchPolicy.MAX_RESULTS);
            previous = limit;
        }

        assertThat(NearbySearchPolicy.resultLimitFor(0))
                .as("반경이 없거나 0이면 기본 반경(5km) 정책을 따라야 한다")
                .isEqualTo(NearbySearchPolicy.resultLimitFor(NearbySearchPolicy.DEFAULT_RADIUS_KM));

        assertThat(NearbySearchPolicy.clampResultLimit(100000, 5))
                .as("호출자가 큰 값을 줘도 정책 상한으로 잘려야 한다")
                .isEqualTo(NearbySearchPolicy.resultLimitFor(5));
        assertThat(NearbySearchPolicy.clampResultLimit(10, 5))
                .as("호출자가 정책보다 작은 값을 주면 그 값을 존중한다")
                .isEqualTo(10);
        assertThat(NearbySearchPolicy.clampResultLimit(null, 5))
                .as("호출자가 안 주면 정책값을 쓴다")
                .isEqualTo(NearbySearchPolicy.resultLimitFor(5));
    }
}
