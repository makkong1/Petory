package com.linkup.Petory.domain.recommendation.client;

import com.linkup.Petory.domain.recommendation.dto.RecommendEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * PetDataApiClient 버그 회귀 테스트
 *
 * 검증 대상:
 * 1. parseCoord — 소수 좌표 문자열 입력 시 Long.parseLong NumberFormatException → null 반환
 * 2. sendEvents — HTTP 전송 없는 no-op (이벤트 소실 확인)
 */
class PetDataApiClientBugTest {

    private PetDataApiClient client;

    @BeforeEach
    void setUp() {
        // 실제 HTTP 요청은 발생하지 않는 테스트 — dummy URL로 인스턴스 생성
        client = new PetDataApiClient(
                "http://localhost:19999", // 연결 불가 포트 — 실수로 호출되면 즉시 실패
                "test-api-key",
                100,
                100
        );
    }

    // ── parseCoord ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseCoord: 소수 형식 좌표 → NumberFormatException silently catch → null 반환 (버그 확인)")
    void parseCoord_소수형식입력_null반환() throws Exception {
        Method method = PetDataApiClient.class.getDeclaredMethod("parseCoord", String.class);
        method.setAccessible(true);

        // 실제 pet-data-api가 반환하는 소수 형식
        Double result = (Double) method.invoke(null, "127.0276368");

        // ★ 버그: Long.parseLong("127.0276368") → NFE → null 반환
        // 수정 후에는 127.0276368 이 그대로 반환되어야 함
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("parseCoord: 정수×10^7 형식은 정상 변환")
    void parseCoord_정수형식입력_정상변환() throws Exception {
        Method method = PetDataApiClient.class.getDeclaredMethod("parseCoord", String.class);
        method.setAccessible(true);

        Double result = (Double) method.invoke(null, "1270276368"); // 127.0276368 × 10^7

        assertThat(result).isEqualTo(127.0276368, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    @DisplayName("parseCoord: null 입력 → null 반환 (정상)")
    void parseCoord_null입력_null반환() throws Exception {
        Method method = PetDataApiClient.class.getDeclaredMethod("parseCoord", String.class);
        method.setAccessible(true);

        assertThat((Double) method.invoke(null, (Object) null)).isNull();
    }

    // ── sendEvents ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendEvents: HTTP 호출 없는 no-op — 예외 없이 즉시 반환 (이벤트 소실 확인)")
    void sendEvents_예외없이반환_HTTP호출없음() {
        RecommendEventRequest request = RecommendEventRequest.builder()
                .requestId("test-req-1")
                .events(List.of())
                .build();

        // ★ 버그: 이 메서드는 log.debug만 남기고 HTTP 전송을 하지 않음
        // localhost:19999에 실제 접속 시도가 있었다면 ConnectException이 발생했을 것
        assertThatCode(() -> client.sendEvents(request)).doesNotThrowAnyException();

        // sendEvents가 no-op이라는 증거:
        // localhost:19999는 연결 불가 → 실제 HTTP가 발생했다면 100ms timeout 내에 예외 발생
        // 예외가 없다는 것 = HTTP 호출이 없었다는 것
    }
}
