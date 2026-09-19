package com.linkup.Petory.domain.care.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지역 매칭이 실제 데이터에서 새지 않는지 잠근다.
 *
 * <p>개발 DB 실측(2026-09-19): 제공자 활동지역이 {@code "서울 강남구"} 240명,
 * {@code "서울특별시 강남구"} 2명, {@code "경기 고양시"} 250명으로 <b>같은 동네인데 표기가
 * 다르다.</b> 게다가 케어 요청 주소는 {@code "서울 마포구 연남동"} 처럼 동까지 내려가서
 * 제공자 쪽(구까지)과 자릿수가 아예 다르다. 접두 일치도, 말단 토큰 비교도 둘 다 샌다.
 */
class CareProviderLocationTest {

    @Test
    @DisplayName("요청은 동까지, 제공자는 구까지여도 같은 구면 맞는다")
    void 자릿수가_달라도_구가_같으면_일치() {
        assertThat(CareProviderLocation.sameArea("서울 강남구 역삼동", "서울 강남구")).isTrue();
    }

    @Test
    @DisplayName("시·도 표기가 흔들려도 같은 구면 맞는다")
    void 시도_표기_흔들림() {
        assertThat(CareProviderLocation.sameArea("서울 강남구 역삼동", "서울특별시 강남구")).isTrue();
    }

    @Test
    @DisplayName("다른 구는 안 맞는다")
    void 다른_구는_불일치() {
        assertThat(CareProviderLocation.sameArea("서울 마포구 연남동", "서울 강남구")).isFalse();
    }

    @Test
    @DisplayName("광역 표기는 키가 아니다 — 안 빼면 서울 전체가 한 동네가 된다")
    void 광역표기는_키가_아니다() {
        assertThat(CareProviderLocation.keysOf("서울특별시 강남구")).containsExactly("강남구");
        assertThat(CareProviderLocation.sameArea("서울특별시 마포구", "서울특별시 강남구")).isFalse();
    }

    @Test
    @DisplayName("시와 구가 같이 있으면 둘 다 키가 된다")
    void 시군구_둘다_키() {
        assertThat(CareProviderLocation.keysOf("경기 성남시 분당구"))
                .containsExactly("성남시", "분당구");
        assertThat(CareProviderLocation.sameArea("경기 성남시 분당구", "경기 성남시")).isTrue();
    }

    @Test
    @DisplayName("주소가 비어 있으면 아무하고도 안 맞는다")
    void 빈_주소는_불일치() {
        assertThat(CareProviderLocation.sameArea(null, "서울 강남구")).isFalse();
        assertThat(CareProviderLocation.sameArea("서울 강남구", null)).isFalse();
        assertThat(CareProviderLocation.sameArea("   ", "서울 강남구")).isFalse();
    }

    @Test
    @DisplayName("시·도 표기가 흔들려도 같은 광역이면 맞는다 — 구가 비었을 때 넓히는 기준")
    void 광역_정규화() {
        assertThat(CareProviderLocation.wideAreaOf("서울특별시 중랑구 묵동")).isEqualTo("서울");
        assertThat(CareProviderLocation.wideAreaOf("서울 강남구")).isEqualTo("서울");
        assertThat(CareProviderLocation.wideAreaOf("경기도 고양시")).isEqualTo("경기");
        assertThat(CareProviderLocation.wideAreaOf("경기 고양시")).isEqualTo("경기");

        // 사용자가 실제로 겪은 조합: 중랑구 요청인데 제공자는 강남구뿐이었다.
        assertThat(CareProviderLocation.sameArea("서울특별시 중랑구 묵동", "서울 강남구")).isFalse();
        assertThat(CareProviderLocation.sameWideArea("서울특별시 중랑구 묵동", "서울 강남구")).isTrue();
    }

    @Test
    @DisplayName("시·도가 다르면 넓혀도 안 맞는다")
    void 다른_광역은_불일치() {
        assertThat(CareProviderLocation.sameWideArea("서울특별시 중랑구", "경기 고양시")).isFalse();
    }
}
