package com.linkup.Petory.domain.recommendation.client;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.recommendation.dto.PetFacilityDto;
import com.linkup.Petory.domain.recommendation.dto.PetFacilityPageDto;
import com.linkup.Petory.domain.recommendation.dto.RecommendCopyRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendCopyResponse;
import com.linkup.Petory.domain.recommendation.dto.RecommendEventRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;
import com.linkup.Petory.domain.recommendation.dto.TrendTimeseriesResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PetDataApiClient {

    private static final String PET_DATA_CORRELATION_HEADER = "X-Request-Id";
    private static final Map<String, String> CONTEXT_LABELS = createContextLabels();

    private final RestClient recommendClient;
    private final RestClient facilityClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetDataApiClient(
            @Value("${app.pet-data-api.base-url}") @NonNull String baseUrl,
            @Value("${app.pet-data-api.api-key}") String apiKey,
            @Value("${app.pet-data-api.timeout-ms:3000}") int timeoutMs,
            @Value("${app.pet-data-api.copy-timeout-ms:35000}") int copyTimeoutMs) {
        log.info("[PetDataApiClient] 초기화 — baseUrl={}, timeoutMs={} (popularity API; copy-timeout unused)",
                baseUrl, timeoutMs);

        // 인기(Popularity) + 트렌드 단일 타임아웃
        this.recommendClient = buildClient(baseUrl, apiKey, timeoutMs);
        // 시설 전체 순회 레거시: pet-data-api에 /facilities 없으면 즉시 폴백
        this.facilityClient = buildClient(baseUrl, apiKey, 30_000);
    }

    private RestClient buildClient(String baseUrl, String apiKey, int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader("X-Caller-Service", "petory")
                .requestFactory(factory)
                .build();
    }

    private static RestClient.RequestHeadersSpec<?> withOptionalCorrelation(
            RestClient.RequestHeadersSpec<?> spec, String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return spec.header(PET_DATA_CORRELATION_HEADER, correlationId);
        }
        return spec;
    }

    /**
     * pet-data-api `GET /popular/{context}` — snack/food/clothes → supplies 알리아스
     * 규격에 맞춤
     */
    static String normalizePopularPathContext(String context) {
        if (context == null || context.isBlank()) {
            return "supplies";
        }
        return switch (context.toLowerCase(Locale.ROOT)) {
            case "snack", "food", "clothes" -> "supplies";
            default -> context.toLowerCase(Locale.ROOT);
        };
    }

    static String normalizeTrendCategory(String context) {
        if (context == null || context.isBlank()) {
            return "grooming";
        }
        return context.toLowerCase(Locale.ROOT);
    }

    /**
     * pet-data-api v4(Popularity Intelligence): {@code POST /recommend} 제거 후
     * {@code GET /popular/{context}}, {@code GET /trends/{category}} 결과를 하나의 레거시
     * {@link RecommendResponse} 형태로 조립한다.
     */
    @SuppressWarnings("UseSpecificCatch")
    public RecommendResponse recommend(RecommendRequest request) {
        String requestId = newRequestId();
        try {
            int topN = request.topN() > 0 ? request.topN() : 5;
            List<RecommendResponse.FacilityItem> facilities = fetchPopular(request.context(), topN, requestId);
            List<RecommendResponse.TrendItem> trends = fetchTrends(request.context(), 15, requestId);

            String recommendation = buildDefaultRecommendation(copyContextLabel(request.context()), facilities, trends);

            log.info(
                    "[PetDataApiClient/recommend→popular+trends] reqId={} popularCtx={} trendCat={} facilities={} trends={}",
                    requestId, normalizePopularPathContext(request.context()),
                    normalizeTrendCategory(request.context()),
                    facilities.size(), trends.size());

            return new RecommendResponse(
                    request.context(),
                    "popular-intelligence-v1",
                    requestId,
                    facilities,
                    trends,
                    recommendation,
                    OffsetDateTime.now(ZoneOffset.UTC).toString());
        } catch (Exception e) {
            log.error("[PetDataApiClient/recommend] 실패 reqId={} {} — {}",
                    requestId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("추천 API 호출 실패: " + e.getMessage(), e);
        }
    }

    public List<RecommendResponse.FacilityItem> fetchPopular(String context, int limit, String correlationId) {
        return fetchPopularAsFacilities(normalizePopularPathContext(context), limit, correlationId);
    }

    public List<RecommendResponse.TrendItem> fetchTrends(String context, int limit, String correlationId) {
        return fetchTrendsAsTrendItems(normalizeTrendCategory(context), limit, correlationId);
    }

    /** pet-data-api에 LLM 카피 엔드포인트 없음 — 규칙 기반 문구만 반환 */
    public RecommendCopyResponse recommendCopy(RecommendCopyRequest request) {
        String requestId = request.requestId() != null ? request.requestId() : newRequestId();
        String text = buildRuleRecommendationCopy(copyContextLabel(request.context()), request.facilities(),
                request.trends());
        log.info("[PetDataApiClient/copy→rule-local] reqId={} chars={}", requestId, text.length());
        return new RecommendCopyResponse(
                requestId, text, "rule", OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    @SuppressWarnings("UseSpecificCatch")
    public void sendEvents(RecommendEventRequest request) {
        log.debug("[PetDataApiClient/events] skipped — popularity API 에 이벤트 수집 경로 없음");
    }

    /**
     * {@code GET /trends/{category}} 스냅샷만 있으므로, 최근 N일 차트용으로 동일 값을 일자별 포인트로 펼친다.
     */
    @SuppressWarnings("UseSpecificCatch")
    public TrendTimeseriesResponse getTrendTimeseries(String category, int days, int topKeywords) {
        String requestId = newRequestId();
        String cat = normalizeTrendCategory(category);
        try {
            log.info("[PetDataApiClient/trends] 요청 스냅샷-only reqId={} category={} limit={}",
                    requestId, cat, topKeywords);

            List<RecommendResponse.TrendItem> keywords = fetchTrendsAsTrendItems(cat, Math.max(topKeywords, 10),
                    requestId);
            List<TrendTimeseriesResponse.Point> points = expandSnapshotToSyntheticSeries(days, keywords);

            return new TrendTimeseriesResponse(cat, days, topKeywords, points);
        } catch (Exception e) {
            log.error("[PetDataApiClient/trends] 실패 reqId={} {} — {}",
                    requestId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("트렌드 시계열 API 호출 실패: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    private List<RecommendResponse.FacilityItem> fetchPopularAsFacilities(
            String popularContext, int limit, String correlationId) {
        try {
            RestClient.ResponseSpec rs = withOptionalCorrelation(
                    recommendClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/popular/{ctx}")
                                    .queryParam("limit", limit)
                                    .build(popularContext)),
                    correlationId)
                    .retrieve();
            String body = rs.body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            List<PopularEntryPayload> parsed = objectMapper.readValue(body, new TypeReference<>() {
            });
            List<RecommendResponse.FacilityItem> out = new ArrayList<>();
            for (PopularEntryPayload p : parsed) {
                if (p.name() == null || p.name().isBlank())
                    continue;
                String addr = p.roadAddress() != null ? p.roadAddress() : p.address();
                Double lat = parseCoord(p.mapY());
                Double lng = parseCoord(p.mapX());
                out.add(new RecommendResponse.FacilityItem(
                        null,
                        null,
                        p.name(),
                        0,
                        addr,
                        lat,
                        lng,
                        p.mentionCount(),
                        p.score(),
                        "popular_blog",
                        p.score(),
                        List.of("popular_blog")));
                if (out.size() >= limit)
                    break;
            }
            return List.copyOf(out);
        } catch (HttpClientErrorException e) {
            int sc = e.getStatusCode().value();
            if (sc == HttpStatus.NOT_FOUND.value()) {
                log.warn("[PetDataApiClient/popular] 404 unknown context {}", popularContext);
                return List.of();
            }
            throw new RuntimeException("pet-data-api popular 클라이언트 오류: HTTP " + sc, e);
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                log.warn("[PetDataApiClient/popular] 503 빈 캐시 context={}: {}", popularContext, e.getMessage());
                return List.of();
            }
            throw new RuntimeException("pet-data-api popular 서버 오류", e);
        } catch (Exception e) {
            log.warn("[PetDataApiClient/popular] 실패 context={}: {}", popularContext, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    private List<RecommendResponse.TrendItem> fetchTrendsAsTrendItems(
            String category, int limit, String correlationId) {
        try {
            String body = withOptionalCorrelation(
                    recommendClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/trends/{cat}")
                                    .queryParam("limit", limit)
                                    .build(category)),
                    correlationId)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            TrendsEnvelope env = objectMapper.readValue(body, TrendsEnvelope.class);
            if (env.keywords() == null) {
                return List.of();
            }
            List<RecommendResponse.TrendItem> rows = new ArrayList<>();
            for (KeywordPayload k : env.keywords()) {
                if (k.keyword() != null && !k.keyword().isBlank()) {
                    rows.add(new RecommendResponse.TrendItem(k.keyword(), k.score()));
                }
            }
            return List.copyOf(rows);
        } catch (HttpClientErrorException e) {
            int sc = e.getStatusCode().value();
            if (sc == HttpStatus.NOT_FOUND.value()) {
                log.warn("[PetDataApiClient/trends-http] 404 unknown category {}", category);
                return List.of();
            }
            throw new RuntimeException("pet-data-api trends 클라이언트 오류: HTTP " + sc, e);
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                log.warn("[PetDataApiClient/trends-http] 503 빈 캐시 category={}: {}", category, e.getMessage());
                return List.of();
            }
            throw new RuntimeException("pet-data-api trends 서버 오류", e);
        } catch (Exception e) {
            log.warn("[PetDataApiClient/trends-http] 실패 category={}: {}", category, e.getMessage());
            return List.of();
        }
    }

    /** 스냅샷 각 키워드를 days 만큼의 일자별 포인트로 복제(Recharts 피벗 호환용) */
    private static List<TrendTimeseriesResponse.Point> expandSnapshotToSyntheticSeries(int days,
            List<RecommendResponse.TrendItem> keywords) {
        if (keywords.isEmpty() || days < 1) {
            return List.of();
        }
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        List<TrendTimeseriesResponse.Point> points = new ArrayList<>(keywords.size() * days);
        for (int ago = days - 1; ago >= 0; ago--) {
            String date = end.minusDays(ago).toString();
            for (RecommendResponse.TrendItem kw : keywords) {
                points.add(new TrendTimeseriesResponse.Point(date, kw.keyword(), kw.score()));
            }
        }
        return points;
    }

    private static String copyContextLabel(String context) {
        if (context == null || context.isBlank()) {
            return "케어";
        }
        return CONTEXT_LABELS.getOrDefault(
                normalizeTrendCategory(context),
                normalizeTrendCategory(context));
    }

    private static String buildDefaultRecommendation(String label, List<RecommendResponse.FacilityItem> facilities,
            List<RecommendResponse.TrendItem> trends) {
        if (facilities.isEmpty() && trends.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" 주변 블로그 기준으로 업소 순위와 키워드를 모았습니다. ");
        if (!facilities.isEmpty()) {
            if (facilities.size() == 1) {
                sb.append(String.format(Locale.ROOT, "블로그에서 자주 이름이 나온 상위 업소에는 %s가 있어요. ",
                        facilities.get(0).name()));
            } else {
                sb.append(String.format(Locale.ROOT,
                        "인기 언급 순위에는 %s 외 %d곳이 있어요. ",
                        facilities.get(0).name(),
                        facilities.size() - 1));
            }
        }
        if (!trends.isEmpty()) {
            sb.append("요즘 ").append(label).append(" 관련 키워드는 ");
            sb.append("'").append(trends.get(0).keyword()).append("'");
            if (trends.size() > 1) {
                sb.append(", '").append(trends.get(1).keyword()).append("'");
            }
            sb.append(" 등입니다.");
        }
        return sb.toString();
    }

    private static String buildRuleRecommendationCopy(String label,
            List<RecommendCopyRequest.CopyFacility> facilities,
            List<RecommendCopyRequest.TrendItem> trends) {
        if ((facilities == null || facilities.isEmpty()) && (trends == null || trends.isEmpty())) {
            return label + " 기준 블로그 인기 순위입니다. 다른 날에는 목록이 달라질 수 있어요.";
        }
        StringBuilder sb = new StringBuilder();
        if (facilities != null && !facilities.isEmpty()) {
            sb.append(String.format(Locale.ROOT,
                    "블로그에서 자주 이름이 나온 %s 카테고리 업소 순위예요.",
                    label));
            sb.append(String.format(Locale.ROOT, " 참고 순위 첫 줄은 '%s'.", facilities.get(0).name()));
        }
        if (trends != null && !trends.isEmpty()) {
            if (sb.length() > 0)
                sb.append(' ');
            sb.append("연관 검색 분위기 키워드는 ").append("'").append(trends.get(0).keyword()).append("' 입니다.");
        }
        return sb.toString();
    }

    @SuppressWarnings("UseSpecificCatch")
    public PetFacilityPageDto fetchFacilitiesPage(long cursor, int limit) {
        PetFacilityPageDto empty = emptyFacilityPage(cursor);
        try {
            String responseBody = facilityClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/facilities")
                            .queryParam("cursor", cursor)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(String.class);
            return objectMapper.readValue(responseBody, PetFacilityPageDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("[PetDataApiClient/facilities] /facilities 없음(popularity 단계)— 동기화 생략");
            return empty;
        } catch (Exception e) {
            log.error("[PetDataApiClient/facilities] 실패 cursor={} {} — {}",
                    cursor, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("시설 목록 API 호출 실패: " + e.getMessage(), e);
        }
    }

    private static PetFacilityPageDto emptyFacilityPage(long ignoredCursorHint) {
        PetFacilityPageDto d = new PetFacilityPageDto();
        d.setItems(Collections.emptyList());
        d.setNextCursor(null);
        d.setHasNext(false);
        return d;
    }

    public List<PetFacilityDto> fetchAllFacilities(int pageSize) {
        List<PetFacilityDto> all = new ArrayList<>();
        long cursor = 0;
        do {
            PetFacilityPageDto page = fetchFacilitiesPage(cursor, pageSize);
            if (page.getItems() != null && !page.getItems().isEmpty()) {
                all.addAll(page.getItems());
            }
            if (!page.isHasNext() || page.getNextCursor() == null || page.getItems() == null
                    || page.getItems().isEmpty())
                break;
            cursor = page.getNextCursor();
        } while (true);
        log.info("[PetDataApiClient/facilities] 전체 수집 완료 total={}", all.size());
        return all;
    }

    private static Double parseCoord(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        try {
            return Long.parseLong(raw.trim()) / 10_000_000.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static Map<String, String> createContextLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("grooming", "미용");
        labels.put("hospital", "동물병원");
        labels.put("pharmacy", "동물약국");
        labels.put("cafe", "카페");
        labels.put("restaurant", "식당");
        labels.put("pension", "펜션");
        labels.put("boarding", "위탁관리");
        labels.put("hotel", "호텔");
        labels.put("supplies", "반려동물용품");
        labels.put("snack", "간식");
        labels.put("food", "사료");
        labels.put("clothes", "의류");
        return Map.copyOf(labels);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PopularEntryPayload(String name,
            @JsonProperty("mention_count") Integer mentionCount,
            double score,
            String address,
            @JsonProperty("road_address") String roadAddress,
            @JsonProperty("map_x") String mapX,
            @JsonProperty("map_y") String mapY,
            String telephone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TrendsEnvelope(String category, List<KeywordPayload> keywords) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KeywordPayload(String keyword, double score) {
    }
}
