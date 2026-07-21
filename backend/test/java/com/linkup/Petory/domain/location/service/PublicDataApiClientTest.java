package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.linkup.Petory.domain.location.dto.PublicDataApiPage;

class PublicDataApiClientTest {

    private PublicDataApiClient newClient() {
        PublicDataApiClient client = new PublicDataApiClient(RestClient.builder());
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.test/api");
        ReflectionTestUtils.setField(client, "serviceKey", "dummy-key");
        ReflectionTestUtils.setField(client, "pageSize", 1000);
        return client;
    }

    @Test
    void 한글키_응답을_DTO로_매핑한다() {
        PublicDataApiClient client = newClient();
        Map<String, Object> body = Map.of(
                "totalCount", 1,
                "data", List.of(Map.of(
                        "시설명", "행복동물병원",
                        "도로명주소", "서울특별시 강남구 테헤란로 152",
                        "위도", "37.5",
                        "경도", "127.0",
                        "반려동물 동반 가능정보", "Y")));

        PublicDataApiPage page = (PublicDataApiPage) ReflectionTestUtils.invokeMethod(client, "parsePage", body);

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        var dto = page.items().get(0);
        assertThat(dto.getFacilityName()).isEqualTo("행복동물병원");
        assertThat(dto.getRoadAddress()).isEqualTo("서울특별시 강남구 테헤란로 152");
        assertThat(dto.getLatitude()).isEqualTo("37.5");
        assertThat(dto.getPetFriendly()).isEqualTo("Y"); // "반려동물 동반 가능정보" → 공백제거 매칭
    }

    @Test
    void data가_없으면_빈_페이지를_반환한다() {
        PublicDataApiClient client = newClient();
        PublicDataApiPage page = (PublicDataApiPage) ReflectionTestUtils.invokeMethod(
                client, "parsePage", Map.of("totalCount", 0));
        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }
}
