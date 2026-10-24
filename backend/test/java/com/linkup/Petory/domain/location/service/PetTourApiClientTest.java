package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;

/**
 * TourAPI 목록 응답 1행 → 적재 DTO 매핑.
 *
 * <p>
 * 실측(2026-10-23)에서 응답의 {@code areacode}·{@code sigungucode}·{@code cat1~3} 이 전부
 * 빈 문자열이었다. 그래서 시도·시군구·카테고리는 전부 파생값이고, 여기가 틀리면 적재는
 * 성공으로 기록되면서 지역·카테고리 검색에서 조용히 안 잡힌다.
 */
class PetTourApiClientTest {

    private PetTourApiClient newClient() {
        PetTourApiClient client = new PetTourApiClient(RestClient.builder());
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.test/KorPetTourService2");
        ReflectionTestUtils.setField(client, "serviceKey", "dummy-key");
        ReflectionTestUtils.setField(client, "pageSize", 100);
        return client;
    }

    /** 실제 응답 1행(가평카페109)에서 가져온 모양. */
    private Map<String, Object> item() {
        Map<String, Object> item = new HashMap<>();
        item.put("contentid", "3083766");
        item.put("title", "가평카페109");
        item.put("addr1", "경기도 가평군 상면 축령로 109");
        item.put("addr2", "");
        item.put("areacode", "");
        item.put("sigungucode", "");
        item.put("cat1", "");
        item.put("mapx", "127.36007330767164");
        item.put("mapy", "37.77466508920391");
        item.put("zipcode", "12448");
        item.put("tel", "");
        item.put("modifiedtime", "20260421112651");
        item.put("lclsSystm2", "FD05");
        return item;
    }

    private PublicDataLocationDTO toDto(Map<String, Object> item, String contentTypeId) {
        return (PublicDataLocationDTO) ReflectionTestUtils.invokeMethod(
                newClient(), "toDto", item, contentTypeId);
    }

    @Test
    void 목록_1행을_적재_DTO로_매핑한다() {
        PublicDataLocationDTO dto = toDto(item(), "39");

        assertThat(dto.getFacilityName()).isEqualTo("가평카페109");
        assertThat(dto.getContentId()).isEqualTo("3083766");
        assertThat(dto.getRoadAddress()).isEqualTo("경기도 가평군 상면 축령로 109");
        // mapy=위도, mapx=경도. 뒤집히면 지도 전체가 엉뚱한 곳을 찍는다.
        assertThat(dto.getLatitude()).isEqualTo("37.77466508920391");
        assertThat(dto.getLongitude()).isEqualTo("127.36007330767164");
        assertThat(dto.getPostalCode()).isEqualTo("12448");
        assertThat(dto.getPhone()).isNull(); // 빈 문자열은 null 로
        // 이 데이터셋은 전부 동반 가능 장소다. "Y" 가 아니면 pet_friendly=false 로 적재된다.
        assertThat(dto.getPetFriendly()).isEqualTo("Y");
        // 적재 변환기가 "yyyy-MM-dd" 만 파싱한다. 원본은 "20260421112651".
        assertThat(dto.getLastUpdatedDate()).isEqualTo("2026-04-21");
    }

    @Test
    void 시도_시군구는_주소를_쪼개서_만든다() {
        PublicDataLocationDTO dto = toDto(item(), "39");
        assertThat(dto.getSidoName()).isEqualTo("경기도");
        assertThat(dto.getSigunguName()).isEqualTo("가평군");
    }

    @Test
    void 시군구가_없는_주소는_시군구를_비운다() {
        Map<String, Object> item = item();
        item.put("addr1", "세종특별자치시 조치원읍 새말로 10");
        PublicDataLocationDTO dto = toDto(item, "39");

        assertThat(dto.getSidoName()).isEqualTo("세종특별자치시");
        assertThat(dto.getSigunguName()).isNull(); // 「조치원읍」을 시군구로 넣지 않는다
    }

    @Test
    void 음식점은_FD05만_카페로_나머지는_식당으로_분류한다() {
        Map<String, Object> cafe = item();
        Map<String, Object> restaurant = item();
        restaurant.put("lclsSystm2", "FD01");

        assertThat(toDto(cafe, "39").getCategory3()).isEqualTo("카페");
        assertThat(toDto(restaurant, "39").getCategory3()).isEqualTo("식당");
        // 중분류도 음식점만 다르다 — 프론트 카테고리 트리가 이 문자열로 블록을 고른다.
        assertThat(toDto(cafe, "39").getCategory2()).isEqualTo("반려동물식당카페");
    }

    @Test
    void 나머지_타입은_반려동반여행_소분류로_분류한다() {
        assertThat(toDto(item(), "32").getCategory3()).isEqualTo("숙박");
        assertThat(toDto(item(), "12").getCategory3()).isEqualTo("여행지");
        assertThat(toDto(item(), "28").getCategory3()).isEqualTo("레포츠");
        assertThat(toDto(item(), "14").getCategory3()).isEqualTo("문화시설");
        assertThat(toDto(item(), "14").getCategory2()).isEqualTo("반려동반여행");
    }

    /** 일일 한도 초과·키 오류가 HTTP 200 으로 온다. 그냥 빈 결과로 삼으면 「데이터가 줄었다」와 섞인다. */
    @Test
    void resultCode가_정상이_아니면_예외로_올린다() {
        Map<String, Object> body = Map.of("response", Map.of(
                "header", Map.of("resultCode", "22",
                        "resultMsg", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR")));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(newClient(), "parse", body))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("resultCode=22");
    }

    @Test
    void items가_빈_문자열이면_빈_목록이다() {
        // 결과가 없을 때 items 가 Map 이 아니라 "" 로 온다.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                newClient(), "items", Map.of("items", ""));
        assertThat(rows).isEmpty();
    }
}
