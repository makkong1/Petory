package com.linkup.Petory.domain.location.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SidoNormalizerTest {

    @DisplayName("구 법정명을 현행명으로 바꾼다 — 886·902행을 놓치게 만든 축")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "강원도,       강원특별자치도",
            "강원,         강원특별자치도",
            "전라북도,     전북특별자치도",
            "전북,         전북특별자치도",
            "제주도,       제주특별자치도",
            "제주,         제주특별자치도"
    })
    void 구_법정명을_현행명으로(String input, String expected) {
        assertThat(SidoNormalizer.normalize(input)).isEqualTo(expected);
    }

    @DisplayName("약식 표기를 정식으로 바꾼다 — PET_DATA_API 가 넣어 둔 53행")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "서울, 서울특별시",
            "부산, 부산광역시",
            "인천, 인천광역시",
            "대구, 대구광역시",
            "광주, 광주광역시",
            "울산, 울산광역시",
            "경기, 경기도",
            "충북, 충청북도",
            "경남, 경상남도",
            "세종, 세종특별자치시"
    })
    void 약식을_정식으로(String input, String expected) {
        assertThat(SidoNormalizer.normalize(input)).isEqualTo(expected);
    }

    @DisplayName("이미 현행명이면 그대로 둔다 (멱등)")
    @ParameterizedTest
    @ValueSource(strings = {
            "서울특별시", "경기도", "강원특별자치도", "전북특별자치도",
            "제주특별자치도", "세종특별자치시", "경상북도", "전라남도"
    })
    void 현행명은_그대로(String canonical) {
        assertThat(SidoNormalizer.normalize(canonical)).isEqualTo(canonical);
    }

    @Test
    @DisplayName("모르는 값은 고치지 않는다 — 임의로 바꾸면 조회가 조용히 빗나간다")
    void 모르는_값은_건드리지_않는다() {
        // TourAPI 가 실제로 주는 비표준 값. 광주광역시인지 전라남도인지 알 수 없어 추측하면 안 된다.
        assertThat(SidoNormalizer.normalize("전남광주통합특별시")).isEqualTo("전남광주통합특별시");
        assertThat(SidoNormalizer.normalize("없는지역")).isEqualTo("없는지역");
    }

    @Test
    @DisplayName("공백은 정리하고, null·빈 값은 null 로 돌려준다")
    void 공백과_빈값() {
        assertThat(SidoNormalizer.normalize("  강원도  ")).isEqualTo("강원특별자치도");
        assertThat(SidoNormalizer.normalize(null)).isNull();
        assertThat(SidoNormalizer.normalize("")).isNull();
        assertThat(SidoNormalizer.normalize("   ")).isNull();
    }
}
