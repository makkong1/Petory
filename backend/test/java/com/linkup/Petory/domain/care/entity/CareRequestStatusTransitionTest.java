package com.linkup.Petory.domain.care.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 상태 전이 규칙을 고정한다.
 *
 * 가드가 없던 시절 transitionTo 는 검사 없이 대입했다. 그래서 COMPLETED -> CANCELLED 가 통했고,
 * 그때 에스크로는 이미 RELEASED 라 환불 분기가 스킵되면서(HOLD 가 아니므로) 로그만 남았다 —
 * 결과는 "상태는 취소인데 돈은 제공자에게 가 있는" 불일치다. 아래 두 테스트가 그 경로를 막는다.
 */
class CareRequestStatusTransitionTest {

    private CareRequest requestWith(CareRequestStatus status) {
        return CareRequest.builder().title("t").description("d").status(status).build();
    }

    @Test
    @DisplayName("완료된 요청은 취소로 돌아갈 수 없다 (상태와 돈이 어긋나는 경로)")
    void 완료_후_취소_불가() {
        CareRequest request = requestWith(CareRequestStatus.COMPLETED);

        assertThatThrownBy(() -> request.transitionTo(CareRequestStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED -> CANCELLED");

        assertThat(request.getStatus()).isEqualTo(CareRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("취소된 요청은 완료로 갈 수 없다")
    void 취소_후_완료_불가() {
        CareRequest request = requestWith(CareRequestStatus.CANCELLED);

        assertThatThrownBy(() -> request.transitionTo(CareRequestStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class);

        assertThat(request.getStatus()).isEqualTo(CareRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("모집 중에서 완료로 건너뛸 수 없다 (거래 확정을 거쳐야 한다)")
    void 모집중_완료_건너뛰기_불가() {
        CareRequest request = requestWith(CareRequestStatus.OPEN);

        assertThatThrownBy(() -> request.transitionTo(CareRequestStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("정상 경로: 모집 중 → 진행 중 → 완료, 완료 시각이 찍힌다")
    void 정상_경로() {
        CareRequest request = requestWith(CareRequestStatus.OPEN);

        request.transitionTo(CareRequestStatus.IN_PROGRESS);
        assertThat(request.getStatus()).isEqualTo(CareRequestStatus.IN_PROGRESS);
        assertThat(request.getCompletedAt()).isNull();

        request.transitionTo(CareRequestStatus.COMPLETED);
        assertThat(request.getStatus()).isEqualTo(CareRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 상태로의 재요청은 예외 없이 무시된다 (재시도 안전)")
    void 같은_상태_재요청은_무해() {
        CareRequest request = requestWith(CareRequestStatus.COMPLETED);

        assertThatCode(() -> request.transitionTo(CareRequestStatus.COMPLETED))
                .doesNotThrowAnyException();
        assertThat(request.getStatus()).isEqualTo(CareRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("취소는 모집 중·진행 중 어디서든 가능하다")
    void 취소는_양쪽에서_가능() {
        assertThatCode(() -> requestWith(CareRequestStatus.OPEN)
                .transitionTo(CareRequestStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> requestWith(CareRequestStatus.IN_PROGRESS)
                .transitionTo(CareRequestStatus.CANCELLED)).doesNotThrowAnyException();
    }
}
