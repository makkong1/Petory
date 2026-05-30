package com.linkup.Petory.domain.place.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilTest {

    @Test void samePointIsZeroDistance() {
        assertThat(GeoUtil.haversineMeters(37.5, 126.9, 37.5, 126.9)).isEqualTo(0.0);
    }

    @Test void knownDistanceIsWithinMargin() {
        // 서울시청(37.5665,126.9780) → 광화문(37.5759,126.9769) 약 1050m
        double dist = GeoUtil.haversineMeters(37.5665, 126.9780, 37.5759, 126.9769);
        assertThat(dist).isBetween(950.0, 1150.0);
    }

    @Test void boundingBoxDeltaCoversRadius() {
        double delta = GeoUtil.latLngDeltaForMeters(500);
        assertThat(delta).isBetween(0.004, 0.006);
    }

    @Test void stringSimilarityIdentical() {
        assertThat(StringSimilarityUtil.normalized("38도씨식당", "38도씨식당")).isEqualTo(1.0);
    }

    @Test void stringSimilarityHighSimilar() {
        double sim = StringSimilarityUtil.normalized("38도씨식당", "38도씨 식당");
        assertThat(sim).isGreaterThan(0.8);
    }

    @Test void stringSimilarityLowDifferent() {
        double sim = StringSimilarityUtil.normalized("동물병원", "강아지카페");
        assertThat(sim).isLessThan(0.5);
    }
}
