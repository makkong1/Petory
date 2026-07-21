package com.linkup.Petory.domain.location.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.linkup.Petory.domain.location.dto.PublicDataApiPage;
import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터포털(odcloud) 반려동물 동반가능 문화시설 오픈API 클라이언트.
 * 페이지 단위 조회 + 한글 응답 키 → PublicDataLocationDTO 매핑을 담당한다.
 */
@Slf4j
@Service
public class PublicDataApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final int MAX_RETRIES = 2;

    /** 정규화(공백 제거)된 한글 컬럼키 → DTO setter 매핑. 라이브 응답에서 키가 다르면 여기만 보정. */
    private static final Map<String, java.util.function.BiConsumer<PublicDataLocationDTO, String>> KEY_TO_FIELD =
            Map.ofEntries(
                    Map.entry("시설명", PublicDataLocationDTO::setFacilityName),
                    Map.entry("카테고리1", PublicDataLocationDTO::setCategory1),
                    Map.entry("카테고리2", PublicDataLocationDTO::setCategory2),
                    Map.entry("카테고리3", PublicDataLocationDTO::setCategory3),
                    Map.entry("시도명칭", PublicDataLocationDTO::setSidoName),
                    Map.entry("시군구명칭", PublicDataLocationDTO::setSigunguName),
                    Map.entry("법정읍면동명칭", PublicDataLocationDTO::setEupmyeondongName),
                    Map.entry("리명칭", PublicDataLocationDTO::setRiName),
                    Map.entry("번지", PublicDataLocationDTO::setBunji),
                    Map.entry("도로명이름", PublicDataLocationDTO::setRoadName),
                    Map.entry("건물번호", PublicDataLocationDTO::setBuildingNumber),
                    Map.entry("위도", PublicDataLocationDTO::setLatitude),
                    Map.entry("경도", PublicDataLocationDTO::setLongitude),
                    Map.entry("우편번호", PublicDataLocationDTO::setPostalCode),
                    Map.entry("도로명주소", PublicDataLocationDTO::setRoadAddress),
                    Map.entry("지번주소", PublicDataLocationDTO::setJibunAddress),
                    Map.entry("전화번호", PublicDataLocationDTO::setPhone),
                    Map.entry("홈페이지", PublicDataLocationDTO::setWebsite),
                    Map.entry("휴무일", PublicDataLocationDTO::setClosedDays),
                    Map.entry("운영시간", PublicDataLocationDTO::setOperatingHours),
                    Map.entry("주차가능여부", PublicDataLocationDTO::setParkingAvailable),
                    Map.entry("입장이용료가격정보", PublicDataLocationDTO::setEntranceFee),
                    Map.entry("반려동물동반가능정보", PublicDataLocationDTO::setPetFriendly),
                    Map.entry("반려동물전용정보", PublicDataLocationDTO::setPetOnly),
                    Map.entry("입장가능동물크기", PublicDataLocationDTO::setPetSizeLimit),
                    Map.entry("반려동물제한사항", PublicDataLocationDTO::setPetRestrictions),
                    Map.entry("장소실내여부", PublicDataLocationDTO::setIndoor),
                    Map.entry("장소실외여부", PublicDataLocationDTO::setOutdoor),
                    Map.entry("기본정보장소설명", PublicDataLocationDTO::setDescription),
                    Map.entry("애견동반추가요금", PublicDataLocationDTO::setPetAdditionalFee),
                    Map.entry("최종작성일", PublicDataLocationDTO::setLastUpdatedDate));

    private final RestClient restClient;

    @Value("${app.public-data.base-url}")
    private String baseUrl;

    @Value("${app.public-data.service-key:}")
    private String serviceKey;

    @Value("${app.public-data.page-size:1000}")
    private int pageSize;

    public PublicDataApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * 전체 페이지를 순회하며 모든 시설을 수집한다.
     *
     * @return 매핑된 전체 시설 DTO 목록
     * @throws IllegalStateException 서비스키 미설정 시
     */
    public List<PublicDataLocationDTO> fetchAll() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("app.public-data.service-key 가 설정되지 않았습니다.");
        }

        List<PublicDataLocationDTO> all = new ArrayList<>();
        int page = 1;
        while (true) {
            PublicDataApiPage result = fetchPage(page);
            if (result.items().isEmpty()) {
                break;
            }
            all.addAll(result.items());
            log.info("공공데이터 조회 진행: page={}, 누적={}, 전체={}", page, all.size(), result.totalCount());
            if (all.size() >= result.totalCount()) {
                break;
            }
            page++;
        }
        return all;
    }

    /**
     * 단일 페이지 조회 (실패 시 최대 {@link #MAX_RETRIES}회 재시도).
     */
    public PublicDataApiPage fetchPage(int page) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("page", page)
                .queryParam("perPage", pageSize)
                .queryParam("returnType", "JSON")
                .queryParam("serviceKey", serviceKey)
                .build()
                .toUri();

        RestClientException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<String, Object> body = restClient.get().uri(uri)
                        .retrieve()
                        .body(MAP_TYPE);
                return parsePage(body);
            } catch (RestClientException e) {
                last = e;
                log.warn("공공데이터 page={} 호출 실패(attempt {}/{}): {}", page, attempt + 1, MAX_RETRIES + 1,
                        e.getMessage());
            }
        }
        throw last;
    }

    @SuppressWarnings("unchecked")
    private PublicDataApiPage parsePage(Map<String, Object> body) {
        if (body == null) {
            return new PublicDataApiPage(List.of(), 0);
        }
        int totalCount = asInt(body.getOrDefault("totalCount", body.get("matchCount")));
        Object dataObj = body.get("data");
        if (!(dataObj instanceof List<?> rows)) {
            return new PublicDataApiPage(List.of(), totalCount);
        }
        List<PublicDataLocationDTO> items = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                items.add(mapRow((Map<String, Object>) map));
            }
        }
        return new PublicDataApiPage(items, totalCount);
    }

    private PublicDataLocationDTO mapRow(Map<String, Object> row) {
        PublicDataLocationDTO dto = new PublicDataLocationDTO();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            var setter = KEY_TO_FIELD.get(normalizeKey(e.getKey()));
            if (setter != null && e.getValue() != null) {
                setter.accept(dto, String.valueOf(e.getValue()).trim());
            }
        }
        return dto;
    }

    /** 키의 모든 공백/괄호/언더스코어를 제거해 미세한 헤더 표기차를 흡수한다. */
    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.replaceAll("[\\s()\\[\\]_]", "");
    }

    private static int asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
