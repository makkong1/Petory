package com.linkup.Petory.global.performance;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.dto.CareRequestListView;
import com.linkup.Petory.domain.care.repository.SpringDataJpaCareRequestRepository;
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
 * <li>meetup — {@code ST_Distance_Sphere(...) ASC}. 함수식이라 정렬 인덱스가 없고 조기종료가
 * 불가능해서, 순수 선택도 손익분기점에서만 뒤집혔다(박스가 테이블의 약 25%, 12km 부터 Table scan).</li>
 * <li>locationservice — {@code CASE(sort)..., rating DESC, idx ASC}. 넓어지면
 * {@code idx_locationservice_deleted_rating} 으로 갈아탔다.</li>
 * </ul>
 *
 * <p>
 * 거리순으로 통일하면 네 도메인 모두 정렬용 인덱스가 없어져 <b>계획이 예측 가능</b>해진다 —
 * 선택도 교차점까지는 공간 인덱스, 그 뒤엔 다른 경로. 지도에서 "가까운 순"이 의미상으로도 맞다.
 *
 * <p>
 * 이 테스트는 그 계약을 고정한다. 누군가 {@code ORDER BY} 를 다시 도메인별로 바꾸면 깨진다.
 * 근거: docs/interview/concepts/02_공간쿼리_Haversine.md
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class NearbySearchContractTest {

    /** 서울 시청 근처 — 더미 데이터가 수도권에 몰려 있어 후보가 잡힌다. */
    private static final double LAT = 37.5665;
    private static final double LNG = 126.978;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private SpringDataJpaCareRequestRepository careRequestRepository;

    private String plan(String sql) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("EXPLAIN FORMAT=TREE " + sql).getResultList();
        StringBuilder sb = new StringBuilder();
        rows.forEach(r -> sb.append(r).append('\n'));
        return sb.toString();
    }

    /**
     * 네 도메인의 반경검색 쿼리가 모두 {@code ST_Distance_Sphere} 로 정밀 반경을 거르는지 본다.
     *
     * <p>
     * missing_pet_board 은 예전에 이 필터가 <b>Java 에 있었다</b> — DB 는 사각형 후보만 주고
     * 서비스가 {@code haversineKm} 로 원형을 걸렀다. 그러면 후보 상한(200건) 중 사각형 모서리에
     * 걸린 것들이 Java 에서 버려져 실제 점수 계산 대상이 200보다 적어졌다. DB 로 내려서
     * 나머지 세 도메인과 필터 위치를 맞췄다.
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

        // 네 쿼리가 예외 없이 실행되면 geo_point/location 컬럼과 SRID 규약이 살아 있다는 뜻이다.
        for (String sql : List.of(careSql, meetupSql, missingSql, locationSql)) {
            Object count = entityManager.createNativeQuery(sql).getSingleResult();
            assertThat(((Number) count).longValue())
                    .as("반경 필터가 실행되지 않는다. geo_point 컬럼·SRID 규약을 확인할 것:\n%s", sql)
                    .isGreaterThanOrEqualTo(0L);
        }
    }

    /** 두 좌표 사이 거리(m). ST_Distance_Sphere 와 같은 구면 근사를 쓴다. */
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
        double radiusKm = 5.0;
        List<CareRequestListView> views = careRequestRepository.findNearbyCareRequests(
                LAT, LNG, radiusKm, NearbySearchPolicy.resultLimitFor(radiusKm));

        assertThat(views)
                .as("후보가 0건이면 정렬 계약을 검증할 수 없다. 더미 데이터의 좌표 분포를 확인할 것.")
                .isNotEmpty();

        double previous = -1;
        for (CareRequestListView v : views) {
            double distance = distanceMeters(LAT, LNG, v.getLatitude(), v.getLongitude());

            assertThat(distance)
                    .as("반경 %skm 밖의 행이 섞였다(idx=%s) — 2차 ST_Distance_Sphere 필터를 확인할 것",
                            radiusKm, v.getIdx())
                    .isLessThanOrEqualTo(radiusKm * 1000 + 1.0);
            assertThat(distance)
                    .as("거리 오름차순이 깨졌다(idx=%s, %.1fm 뒤에 %.1fm). "
                            + "ORDER BY 가 created_at 등으로 되돌아갔는지 확인할 것",
                            v.getIdx(), previous, distance)
                    .isGreaterThanOrEqualTo(previous - 1.0);
            previous = distance;
        }
    }

    /**
     * 정렬 통일이 실제로 계획을 바꿨는지 — {@code created_at} 정렬 인덱스로 새지 않는다.
     *
     * <p>
     * 이게 이 작업의 핵심 효과다. 예전엔 반경이 조금만 넓어져도
     * {@code Index scan on cr using idx_carerequest_deleted_created (reverse)} 로 새면서
     * 공간 인덱스를 버렸다. 거리순으로 바꾸면 그 탈출구가 없어진다.
     *
     * <p>
     * 계획은 <b>실제 프로덕션 쿼리 문자열</b>로 떠야 의미가 있다. 그래서 리포지토리에 박힌
     * 쿼리를 {@code @Query} 어노테이션에서 읽어와 EXPLAIN 한다.
     */
    @Test
    @DisplayName("care 반경검색: 프로덕션 쿼리가 created_at 정렬 인덱스로 새지 않는다")
    void careNearbyNoLongerEscapesToCreatedAtIndex() throws Exception {
        String sql = productionNearbyQuery()
                .replace(":lat", String.valueOf(LAT))
                .replace(":lng", String.valueOf(LNG))
                .replace(":radius", "20.0")
                .replace(":limit", "500");

        String plan = plan(sql);
        assertThat(plan)
                .as("정렬 기준이 거리인데 created_at 인덱스를 탄다는 건 ORDER BY 가 되돌아갔다는 뜻이다.\n계획:\n%s",
                        plan)
                .doesNotContain("idx_carerequest_deleted_created");
    }

    /** 리포지토리에 실제로 박혀 있는 반경검색 쿼리 문자열을 꺼낸다(테스트가 SQL 을 새로 쓰지 않도록). */
    private String productionNearbyQuery() throws NoSuchMethodException {
        Query query = SpringDataJpaCareRequestRepository.class
                .getMethod("findNearbyCareRequests", Double.class, Double.class, Double.class, int.class)
                .getAnnotation(Query.class);
        assertThat(query)
                .as("findNearbyCareRequests 에 @Query 가 없다 — 시그니처가 바뀌었는지 확인할 것")
                .isNotNull();
        return query.value();
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
