package com.linkup.Petory.domain.petRecommendation.client;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeRequest;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * {@code petory-nlp-server}(FastAPI) 반려생활 의도 분석 API를 호출하는 HTTP 클라이언트.
 *
 * <p>
 * Spring Boot 메인 앱과 NLP 서버는 프로세스가 분리되어 있으며, 이 클래스가 그 사이의 유일한 동기 HTTP 진입점이다.
 * 호출자는
 * {@link com.linkup.Petory.domain.petRecommendation.service.PetIntentSignalEventListener}
 * (비동기 signal 저장)와
 * {@link com.linkup.Petory.domain.petRecommendation.service.PetRecommendationService}
 * (주변 시설 추천)이다.
 *
 * <h3>엔드포인트</h3> {@code POST {app.pet-intent.base-url}/api/pet-intent/analyze}
 * <br>요청 본문: {@link PetIntentAnalyzeRequest} (text, petType)
 * <br>응답 본문: {@link PetIntentAnalyzeResponse} (intentDomain, confidence,
 * recommendedCategories 등)
 *
 * <h3>설정 ({@code application.properties} / profile)</h3>
 * <ul>
 * <li>{@code app.pet-intent.base-url} — NLP 서버 루트 URL (기본
 * {@code http://localhost:8000})</li>
 * <li>{@code app.pet-intent.timeout-ms} — connect/read 공통 타임아웃 ms (기본
 * {@code 3000})</li>
 * </ul>
 *
 * <h3>실패 처리</h3>
 * 타임아웃·연결 거부·4xx/5xx 등 {@link RestClientException}은 모두 잡아
 * {@link Optional#empty()}를 반환한다. 호출 측은 NLP 장애 시에도 본 기능(게시/케어/검색, 추천 API)이 깨지지
 * 않도록 fallback·skip 한다.
 */
@Slf4j
@Component
public class PetIntentClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    /**
     * NLP 서버 HTTP 호출 전용 클라이언트 생성 {@link RestTemplateBuilder}로 타임아웃이 적용된 전용
     * {@link RestTemplate}을 생성한다. Bean 공유 RestTemplate과 분리해 NLP 호출만 짧은
     * timeout으로 제한한다.
     */
    public PetIntentClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.pet-intent.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.pet-intent.timeout-ms:3000}") long timeoutMs) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
        log.info("{}", restTemplate);
        log.info("[PetIntentClient] 초기화 baseUrl={} timeoutMs={}", baseUrl, timeoutMs);
    }

    /**
     * 사용자 입력 텍스트를 NLP 서버에 보내 의도·카테고리·confidence 등을 분석한다.
     *
     * @param text 분석 대상 문장 (게시글, 케어 요청, 위치 검색 키워드 등). null/blank면 호출자가 필터링하는 것이
     * 일반적
     * @param petType 반려동물 종류 힌트 (예: DOG). 없으면 null — Python 측에서 선택적 사용
     * @return 분석 성공 시 응답 DTO, 실패·빈 본문 시 {@link Optional#empty()}
     */
    /**
     * Python PetType enum(DOG|CAT|OTHER) 기준으로 Java petType을 정규화한다.
     */
    private static String normalizePetType(String petType) {
        if (petType == null) {
            return null;
        }
        return switch (petType) {
            case "DOG", "CAT" ->
                petType;
            default ->
                "OTHER";
        };
    }

    public Optional<PetIntentAnalyzeResponse> analyze(String text, String petType) {
        int textLen = text != null ? text.length() : 0;
        // 본문 전체는 로그에 남기지 않음 (개인정보·과도한 로그 방지)
        log.debug("[PetIntentClient] analyze 요청 textLen={} petType={}", textLen, petType);

        try {
            PetIntentAnalyzeRequest req = PetIntentAnalyzeRequest.builder()
                    .text(text)
                    .petType(normalizePetType(petType))
                    .build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PetIntentAnalyzeRequest> entity = new HttpEntity<>(req, headers);

            ResponseEntity<PetIntentAnalyzeResponse> resp = restTemplate.postForEntity(
                    baseUrl + "/api/pet-intent/analyze",
                    entity,
                    PetIntentAnalyzeResponse.class
            );

            PetIntentAnalyzeResponse body = resp.getBody();
            if (body == null) {
                // HTTP 2xx 이지만 body 없음 — 역직렬화/서버 버그 가능성
                log.warn("[PetIntentClient] analyze 응답 본문 없음 status={}", resp.getStatusCode());
                return Optional.empty();
            }

            log.debug("[PetIntentClient] analyze 성공 domain={} confidence={}",
                    body.getIntentDomain(), body.getConfidence());
            return Optional.of(body);
        } catch (RestClientException e) {
            // 타임아웃, Connection refused, 4xx/5xx 등 — 호출자는 empty로 fallback/skip
            log.warn("[PetIntentClient] Python 서버 호출 실패 baseUrl={} textLen={} cause={}",
                    baseUrl, textLen, e.getMessage());
            return Optional.empty();
        }
    }
}
