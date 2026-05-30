package com.linkup.Petory.domain.place.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static com.linkup.Petory.domain.place.service.NameQualityChecker.NameCheckResult.*;

class NameQualityCheckerTest {

    private final NameQualityChecker checker = new NameQualityChecker();

    @Test void hardBlacklistReturnsHardReject() {
        assertThat(checker.check("식사")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("기념일")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("맛집")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("주말")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("데이트")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("공원")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("산책")).isEqualTo(HARD_REJECT);
    }

    @Test void softBlacklistReturnsSoftRisk() {
        assertThat(checker.check("프렌치")).isEqualTo(SOFT_RISK);
        assertThat(checker.check("벚꽃")).isEqualTo(SOFT_RISK);
        assertThat(checker.check("감성")).isEqualTo(SOFT_RISK);
        assertThat(checker.check("라운지")).isEqualTo(SOFT_RISK);
        assertThat(checker.check("살롱")).isEqualTo(SOFT_RISK);
    }

    @Test void twoCharsOrLessReturnsHardReject() {
        assertThat(checker.check("강")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("AB")).isEqualTo(HARD_REJECT);
    }

    @Test void nullOrEmptyReturnsHardReject() {
        assertThat(checker.check(null)).isEqualTo(HARD_REJECT);
        assertThat(checker.check("")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("   ")).isEqualTo(HARD_REJECT);
    }

    @Test void specialCharsOnlyReturnsHardReject() {
        assertThat(checker.check("!!??")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("---")).isEqualTo(HARD_REJECT);
    }

    @Test void goodNameReturnsOk() {
        assertThat(checker.check("38도씨식당")).isEqualTo(OK);
        assertThat(checker.check("개떼놀이터")).isEqualTo(OK);
        assertThat(checker.check("23플래터")).isEqualTo(OK);
    }

    @Test void isGoodQualityRequiresFourChars() {
        assertThat(checker.isGoodQuality("개떼놀이터")).isTrue();
        assertThat(checker.isGoodQuality("강아")).isFalse();
        assertThat(checker.isGoodQuality("맛집")).isFalse();
    }
}
