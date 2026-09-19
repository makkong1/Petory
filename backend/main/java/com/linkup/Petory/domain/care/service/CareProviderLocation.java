package com.linkup.Petory.domain.care.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 주소 문자열에서 지역 비교용 키(시/군/구)를 뽑는다.
 *
 * <p>왜 이게 필요한가: 비교하려는 두 값의 <b>자릿수가 다르다.</b>
 * <ul>
 *   <li>케어 요청 주소 — {@code "서울 마포구 연남동"} (동까지)</li>
 *   <li>제공자 활동지역 — {@code "서울 강남구"} (구까지)</li>
 * </ul>
 * 그대로 비교하면 아무것도 안 맞고, 접두 일치도 샌다 — 같은 동네인데
 * {@code "서울 강남구"}(240명)와 {@code "서울특별시 강남구"}(2명)가 섞여 있기 때문이다.
 *
 * <p>그래서 <b>시/군/구 토큰만</b> 뽑아 교집합으로 본다. 광역 표기(특별시·광역시·특별자치시)는
 * 시로 끝나지만 자치구 레벨이 아니라서 제외한다 — 안 빼면 "서울특별시"가 키가 되어 서울 전체가
 * 한 동네로 묶인다.
 *
 * <pre>
 *   "서울 마포구 연남동"   -> [마포구]
 *   "서울특별시 강남구"    -> [강남구]
 *   "경기 성남시 분당구"   -> [성남시, 분당구]
 *   "경기 고양시"          -> [고양시]
 * </pre>
 */
final class CareProviderLocation {

    private CareProviderLocation() {
    }

    private static final String[] WIDE_AREA_SUFFIXES = { "특별시", "광역시", "특별자치시", "특별자치도" };

    /** 주소에서 시/군/구 토큰을 뽑는다. 하나도 없으면 빈 집합. */
    static Set<String> keysOf(String address) {
        Set<String> keys = new LinkedHashSet<>();
        if (address == null || address.isBlank()) {
            return keys;
        }
        for (String token : address.trim().split("\\s+")) {
            if (isWideArea(token)) {
                continue;
            }
            if (token.endsWith("시") || token.endsWith("군") || token.endsWith("구")) {
                keys.add(token);
            }
        }
        return keys;
    }

    /**
     * 시·도 이름. 표기가 흔들려도 같은 값이 나오도록 접미사를 떼어 정규화한다
     * ({@code "서울특별시" -> "서울"}, {@code "경기도" -> "경기"}).
     *
     * <p>구 단위로 맞추면 후보가 0인 경우가 흔해서(제공자가 그 구에 없을 뿐, 옆 구엔 있다)
     * 한 단계 넓혀 찾을 때 쓴다.
     */
    static String wideAreaOf(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String head = address.trim().split("\\s+")[0];
        for (String suffix : new String[] { "특별자치시", "특별자치도", "특별시", "광역시", "도" }) {
            if (head.length() > suffix.length() && head.endsWith(suffix)) {
                return head.substring(0, head.length() - suffix.length());
            }
        }
        return head;
    }

    /** 두 주소가 같은 시·도인가. 구가 달라도 같은 광역이면 참. */
    static boolean sameWideArea(String addressA, String addressB) {
        String a = wideAreaOf(addressA);
        String b = wideAreaOf(addressB);
        return a != null && a.equals(b);
    }

    /** 두 주소가 같은 시/군/구를 하나라도 공유하는가. */
    static boolean sameArea(String addressA, String addressB) {
        Set<String> a = keysOf(addressA);
        if (a.isEmpty()) {
            return false;
        }
        for (String key : keysOf(addressB)) {
            if (a.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWideArea(String token) {
        return Arrays.stream(WIDE_AREA_SUFFIXES).anyMatch(token::endsWith);
    }
}
