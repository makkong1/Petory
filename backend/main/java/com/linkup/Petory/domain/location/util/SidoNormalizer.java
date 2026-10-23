package com.linkup.Petory.domain.location.util;

import java.util.Map;

/**
 * 시도명을 현행 법정명으로 통일한다.
 *
 * <p>
 * 적재와 검색 양쪽에서 통과시켜야 한다. {@code findBySido} 가 {@code WHERE sido = :sido}
 * 정확일치이므로, 한쪽만 통과시키면 그쪽이 그대로 어긋난다.
 *
 * <p>
 * 🔴 <b>고치는 결함</b> — 프론트({@code UnifiedPetMapPage.js}) 가 사용자가 입력한 지역명을 그대로
 * {@code sido} 파라미터로 보낸다. 그래서 2026-10-23 측정에서 이렇게 나왔다:
 *
 * <pre>
 *   「강원특별자치도」(2023-06 현행명) 검색 →   2건  (실제 886)
 *   「전북특별자치도」(2024-01 현행명) 검색 →   0건  (실제 902)
 *   「강원도」      (구명)            검색 → 884건
 * </pre>
 *
 * 현행 법정명으로 검색하면 사실상 아무것도 나오지 않았다.
 *
 * <p>
 * ⚠️ <b>이 맵과 {@code V23} 마이그레이션은 같은 목록을 쓴다.</b> 한쪽만 고치면 적재된 값과
 * 검색 입력이 다시 갈린다. 둘을 함께 고칠 것.
 */
public final class SidoNormalizer {

    /** 별칭(약식·구 법정명) → 현행 법정명. 값 쪽은 전부 아래 17개 중 하나다. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            // 약식 — PET_DATA_API 출처가 이렇게 넣어 뒀다(53행)
            Map.entry("서울", "서울특별시"),
            Map.entry("서울시", "서울특별시"),
            Map.entry("부산", "부산광역시"),
            Map.entry("부산시", "부산광역시"),
            Map.entry("대구", "대구광역시"),
            Map.entry("대구시", "대구광역시"),
            Map.entry("인천", "인천광역시"),
            Map.entry("인천시", "인천광역시"),
            Map.entry("광주", "광주광역시"),
            Map.entry("광주시", "광주광역시"),
            Map.entry("대전", "대전광역시"),
            Map.entry("대전시", "대전광역시"),
            Map.entry("울산", "울산광역시"),
            Map.entry("울산시", "울산광역시"),
            Map.entry("세종", "세종특별자치시"),
            Map.entry("세종시", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("충북", "충청북도"),
            Map.entry("충남", "충청남도"),
            Map.entry("전남", "전라남도"),
            Map.entry("경북", "경상북도"),
            Map.entry("경남", "경상남도"),
            // 구 법정명 → 현행명. 이게 886·902행을 놓치게 만든 축이다
            Map.entry("강원", "강원특별자치도"),
            Map.entry("강원도", "강원특별자치도"),
            Map.entry("전북", "전북특별자치도"),
            Map.entry("전라북도", "전북특별자치도"),
            Map.entry("제주", "제주특별자치도"),
            Map.entry("제주도", "제주특별자치도"));

    private SidoNormalizer() {
    }

    /**
     * 시도명을 현행 법정명으로 바꾼다. 아는 별칭이 아니면 공백만 정리해 그대로 돌려준다 —
     * 모르는 값을 임의로 고치면 조회가 조용히 빗나간다.
     *
     * @param sido 원본 시도명 (null 허용)
     * @return 현행 법정명, 또는 매핑이 없으면 trim 한 원본. 입력이 null·공백이면 null
     */
    public static String normalize(String sido) {
        if (sido == null) {
            return null;
        }
        String trimmed = sido.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return ALIASES.getOrDefault(trimmed, trimmed);
    }
}
