package com.linkup.Petory.domain.location.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * 한국관광공사 반려동물 동반여행 서비스(data.go.kr 15135102) 클라이언트.
 *
 * <p>
 * 응답을 기존 적재 DTO({@link PublicDataLocationDTO})로 맞춰 {@link PublicDataSyncService} 의
 * 멱등 upsert 파이프라인을 그대로 탄다.
 *
 * <p>
 * 🔴 <b>목록 응답이 비어서 주는 칸이 있다</b> (2026-10-23 실측, {@code areaBasedList2}):
 * {@code areacode}·{@code sigungucode}·{@code cat1~3} 이 전부 빈 문자열이다. 그래서
 * <b>시도·시군구는 {@code addr1} 을 쪼개서</b>, <b>카테고리는 {@code contentTypeId} 에서</b> 만든다.
 * ({@code addr1}·{@code mapx}·{@code mapy} 는 표본 371건에서 100% 채워져 있었다.)
 *
 * <p>
 * 🔴 <b>{@code acmpyTypeCd} 는 목록에 없다.</b> {@code detailPetTour2} 가 {@code contentId} 를
 * 필수로 받아 장소마다 1회씩 호출해야 한다. 전체 1,022건 &gt; 개발계정 한도 1,000회/일 이므로
 * {@link #fetchPetDetail} 은 <b>실제로 새로 적재되는 행만</b> 부른다
 * ({@link PetTourSyncService}). 첫 실행 876회, 이후 0회다.
 */
@Slf4j
@Service
public class PetTourApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final int MAX_RETRIES = 2;

    /**
     * 적재 대상 contentTypeId — 음식점 39 · 숙박 32 · 관광지 12 · 레포츠 28 · 문화시설 14.
     * <b>쇼핑(38)은 뺀다</b> — 전국 9,660건 중 8,639건(89%)이 거기 있는데 약국·안경원·의원·백화점
     * 매장이고, 반려동물을 데리고 갈 곳이 아니다(2026-10-23 표본 300건 확인).
     */
    private static final List<String> CONTENT_TYPE_IDS = List.of("39", "32", "12", "28", "14");

    /** 전부 「반려동물업」. 개발 DB 24,130행 중 23,000행 이상이 이 대분류다. */
    private static final String CATEGORY1 = "반려동물업";

    private final RestClient restClient;

    @Value("${app.pet-tour.base-url:https://apis.data.go.kr/B551011/KorPetTourService2}")
    private String baseUrl;

    /**
     * data.go.kr 키는 계정 단위라 기존 공공데이터 키를 그대로 쓴다.
     * ⚠️ <b>디코딩 키</b>여야 한다 — 인코딩 키({@code %2B} 가 들어있는 것)를 넣으면
     * {@link URLEncoder} 가 한 번 더 인코딩해 이중 인코딩된다.
     */
    @Value("${app.pet-tour.service-key:${app.public-data.service-key:}}")
    private String serviceKey;

    @Value("${app.pet-tour.page-size:100}")
    private int pageSize;

    public PetTourApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * 적재 대상 contentTypeId 전부를 페이지 순회해 수집한다.
     *
     * @throws IllegalStateException 서비스키 미설정 시
     */
    public List<PublicDataLocationDTO> fetchAll() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("app.pet-tour.service-key(또는 app.public-data.service-key) 가 설정되지 않았습니다.");
        }

        List<PublicDataLocationDTO> all = new ArrayList<>();
        for (String contentTypeId : CONTENT_TYPE_IDS) {
            int page = 1;
            int collected = 0;
            while (true) {
                Map<String, Object> body = call("areaBasedList2", Map.of(
                        "contentTypeId", contentTypeId,
                        "numOfRows", String.valueOf(pageSize),
                        "pageNo", String.valueOf(page),
                        "arrange", "A"));
                List<Map<String, Object>> items = items(body);
                if (items.isEmpty()) {
                    break;
                }
                for (Map<String, Object> item : items) {
                    all.add(toDto(item, contentTypeId));
                }
                collected += items.size();
                int totalCount = asInt(body.get("totalCount"));
                log.info("TourAPI 조회: contentTypeId={}, page={}, 누적={}, 전체={}",
                        contentTypeId, page, collected, totalCount);
                if (collected >= totalCount) {
                    break;
                }
                page++;
            }
        }
        return all;
    }

    /**
     * 장소 1건의 반려동물 상세. 실패하면 null — 상세는 부가 정보이고, 이것 때문에 적재 자체를
     * 멈추면 장소가 아예 안 들어온다. 일일 한도 소진도 여기로 떨어진다.
     */
    public PetTourDetail fetchPetDetail(String contentId) {
        if (!StringUtils.hasText(contentId)) {
            return null;
        }
        try {
            List<Map<String, Object>> items = items(call("detailPetTour2", Map.of("contentId", contentId)));
            if (items.isEmpty()) {
                return null;
            }
            Map<String, Object> item = items.get(0);
            return new PetTourDetail(str(item, "acmpyTypeCd"), str(item, "etcAcmpyInfo"));
        } catch (RestClientException e) {
            log.warn("TourAPI 상세 조회 실패: contentId={}, {}", contentId, e.getMessage());
            return null;
        }
    }

    /** 반려동물 상세 중 믿을 수 있는 두 칸. 나머지 7칸은 실측에서 거의 비어 있었다. */
    public record PetTourDetail(String acmpyTypeCd, String etcAcmpyInfo) {
    }

    /** 공통 호출 — 공통 파라미터 부착, resultCode 검사, 재시도. */
    private Map<String, Object> call(String operation, Map<String, String> params) {
        // 디코딩 키의 '+','/','=' 를 퍼센트 인코딩한 뒤 build(true) 로 재인코딩을 막는다.
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/" + operation)
                .queryParam("serviceKey", URLEncoder.encode(serviceKey, StandardCharsets.UTF_8))
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "Petory")
                .queryParam("_type", "json");
        params.forEach(builder::queryParam);
        URI uri = builder.build(true).toUri();

        RestClientException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return parse(restClient.get().uri(uri).retrieve().body(MAP_TYPE));
            } catch (HttpClientErrorException.TooManyRequests e) {
                // 일일 요청제한 초과. 재시도하면 같은 429 를 세 번 받고 시간만 쓴다
                // (2026-10-24 한도 소진 후 행당 3회씩 불러 수십 분을 태웠다).
                throw e;
            } catch (RestClientException e) {
                last = e;
                log.warn("TourAPI {} 호출 실패(attempt {}/{}): {}", operation, attempt + 1, MAX_RETRIES + 1,
                        e.getMessage());
            }
        }
        throw last;
    }

    /**
     * {@code response.body} 를 꺼낸다. 오류는 HTTP 200 + resultCode 로 오므로(일일 한도 초과 포함)
     * 예외로 바꿔 올린다 — 안 그러면 「조회 결과가 줄었다」와 구분되지 않는다.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(Map<String, Object> body) {
        if (body == null) {
            throw new RestClientException("TourAPI 응답이 비었습니다.");
        }
        if (!(body.get("response") instanceof Map<?, ?> response)) {
            // 키 오류 등은 {"resultCode":"30",...} 형태로 response 없이 온다.
            throw new RestClientException("TourAPI 오류 응답: " + body);
        }
        if (response.get("header") instanceof Map<?, ?> header) {
            String code = String.valueOf(header.get("resultCode"));
            if (!"0000".equals(code)) {
                throw new RestClientException("TourAPI resultCode=" + code + " msg=" + header.get("resultMsg"));
            }
        }
        Object responseBody = response.get("body");
        return (responseBody instanceof Map<?, ?> map) ? (Map<String, Object>) map : Map.of();
    }

    /** {@code body.items} 는 결과가 없으면 Map 이 아니라 빈 문자열로 온다. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> body) {
        if (!(body.get("items") instanceof Map<?, ?> items)) {
            return List.of();
        }
        Object item = items.get("item");
        if (item instanceof List<?> rows) {
            return (List<Map<String, Object>>) rows;
        }
        if (item instanceof Map<?, ?> single) {
            return List.of((Map<String, Object>) single);
        }
        return List.of();
    }

    private PublicDataLocationDTO toDto(Map<String, Object> item, String contentTypeId) {
        PublicDataLocationDTO dto = new PublicDataLocationDTO();
        dto.setFacilityName(str(item, "title"));
        dto.setContentId(str(item, "contentid"));

        // addr2 는 상세주소인데 실측 표본에서 전부 비어 있었고, 붙이면 기존 행과의
        // (이름,주소) 중복 판정이 어긋난다. addr1 만 쓴다.
        String addr = str(item, "addr1");
        dto.setRoadAddress(addr);
        dto.setSidoName(token(addr, 0));
        dto.setSigunguName(sigungu(addr));

        dto.setCategory1(CATEGORY1);
        dto.setCategory2(category2(contentTypeId));
        dto.setCategory3(category3(contentTypeId, str(item, "lclsSystm2")));

        dto.setLatitude(str(item, "mapy")); // mapy=위도, mapx=경도
        dto.setLongitude(str(item, "mapx"));
        dto.setPostalCode(str(item, "zipcode"));
        dto.setPhone(str(item, "tel"));
        // 이 데이터셋에 실린 장소는 전부 반려동물 동반 가능이다. 구역 구분(전구역/일부구역)은
        // pet_zone_type 으로 따로 들어간다.
        dto.setPetFriendly("Y");
        dto.setLastUpdatedDate(toDate(str(item, "modifiedtime")));
        return dto;
    }

    /** 기존 DB 와 같은 중분류 어휘를 쓴다 — 프론트 카테고리 트리가 이 문자열로 필터한다. */
    private static String category2(String contentTypeId) {
        return "39".equals(contentTypeId) ? "반려동물식당카페" : "반려동반여행";
    }

    private static String category3(String contentTypeId, String lclsSystm2) {
        switch (contentTypeId) {
            case "39":
                // FD05 가 카페·디저트다(실측 71건 중 44건: 가평카페109·감자밭·더스크커피랩…).
                // 나머지 FD01·FD02 는 한우타운·횟집·막국수 같은 식당이었다.
                return "FD05".equals(lclsSystm2) ? "카페" : "식당";
            case "32":
                return "숙박";
            case "12":
                return "여행지";
            case "28":
                return "레포츠";
            case "14":
                return "문화시설";
            default:
                return null;
        }
    }

    /** 시군구는 「…시/군/구」로 끝나는 둘째 토큰만 취한다. 세종처럼 시군구가 없는 곳이 있다. */
    private static String sigungu(String address) {
        String second = token(address, 1);
        if (second == null) {
            return null;
        }
        return (second.endsWith("시") || second.endsWith("군") || second.endsWith("구")) ? second : null;
    }

    private static String token(String address, int index) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        return (index < tokens.length) ? tokens[index] : null;
    }

    /** modifiedtime 은 "yyyyMMddHHmmss" 다. 적재 변환기가 "yyyy-MM-dd" 만 파싱한다. */
    private static String toDate(String value) {
        if (value == null || value.length() < 8) {
            return null;
        }
        return value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8);
    }

    private static String str(Map<String, Object> item, String key) {
        Object value = item.get(key);
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
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
