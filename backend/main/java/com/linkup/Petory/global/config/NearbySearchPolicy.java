package com.linkup.Petory.global.config;

/**
 * ====================================================================================
 * 지도 반경검색의 결과 상한을 한 곳에서 정한다 (2026-07-31)
 * ====================================================================================
 *
 * <p>
 * <b>왜 만들었나</b> — 예전엔 상한이 네 곳에 흩어져 있었다.
 * <ul>
 * <li>프론트 {@code ZOOM_LIMIT_TABLE} — meetup 30~800, care 20~400 (줌 레벨 기준)</li>
 * <li>프론트 {@code LOCATION_RESULT_LIMIT} — 300 고정</li>
 * <li>백엔드 컨트롤러 기본값 — care 200, location 100</li>
 * <li>서비스 상수 — {@code HOME_MISSING_CANDIDATE_LIMIT} 200</li>
 * </ul>
 *
 * <p>
 * 게다가 프론트는 상한을 <b>줌 레벨</b>에 묶었는데 쿼리가 읽을 행 수를 결정하는 건
 * <b>반경</b>이다. 둘은 따로 움직인다 — 반경 슬라이더를 5km 로 두고 지도만 축소하면
 * 레벨이 12가 되어 상한이 400 으로 뛰지만 쿼리 반경은 그대로 5km 다. 상한이 쿼리와
 * 무관한 값을 따라다니는 셈이었다.
 *
 * <p>
 * <b>그래서 키를 반경으로 바꾸고 백엔드로 옮겼다.</b> 프론트는 반경만 보내고 상한은
 * 관여하지 않는다.
 *
 * <p>
 * <b>숫자 근거</b> — 로컬 실측 매치 수(서울 중심)에 맞췄다. 반경이 커지면 결과도 커지므로
 * 상한도 같이 올리되, 지도 마커로 의미 있는 범위에서 자른다.
 *
 * <pre>
 *   meetup : 5km→75건   10km→292건   20km→712건   100km→1,992건
 *   care   : 5km→87건   10km→371건   50km→1,800건
 * </pre>
 *
 * 상한이 매치 수보다 작으면 옵티마이저가 조기종료 계획을 고를 수 있어 공간 인덱스를
 * 안 쓸 수 있다. 그래서 좁은 반경에서는 매치 수보다 넉넉하게 잡는다.
 * (근거: docs/interview/concepts/02_공간쿼리_Haversine.md §3-2)
 * ====================================================================================
 */
public final class NearbySearchPolicy {

    private NearbySearchPolicy() {
    }

    /** 반경 파라미터가 없을 때 쓰는 기본 반경 (km). 프론트 지도 기본값과 같다. */
    public static final double DEFAULT_RADIUS_KM = 5.0;

    /** 어떤 요청이 와도 넘길 수 없는 절대 상한. */
    public static final int MAX_RESULTS = 800;

    /**
     * 반경(km)에 대응하는 결과 상한을 돌려준다.
     *
     * @param radiusKm 검색 반경(km). 0 이하면 {@link #DEFAULT_RADIUS_KM} 로 취급한다.
     */
    public static int resultLimitFor(double radiusKm) {
        double r = radiusKm > 0 ? radiusKm : DEFAULT_RADIUS_KM;
        if (r <= 2) {
            return 100;
        }
        if (r <= 5) {
            return 200;
        }
        if (r <= 10) {
            return 350;
        }
        if (r <= 20) {
            return 500;
        }
        return MAX_RESULTS;
    }

    /**
     * 호출자가 상한을 직접 지정한 경우에도 정책 상한을 넘지 못하게 자른다.
     *
     * @param requested 호출자가 준 값 (null 이면 정책값을 그대로 쓴다)
     */
    public static int clampResultLimit(Integer requested, double radiusKm) {
        int policy = resultLimitFor(radiusKm);
        if (requested == null || requested <= 0) {
            return policy;
        }
        return Math.min(requested, policy);
    }
}
