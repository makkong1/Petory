package com.linkup.Petory.domain.location.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.linkup.Petory.domain.location.dto.SeoulPetNewsCrawlResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * {@code petory-nlp-server} 의 서울시 보도자료 크롤러를 부른다.
 *
 * <p>
 * base-url 은 {@link com.linkup.Petory.domain.petRecommendation.client.PetIntentClient}
 * 와 <b>같은 속성</b>({@code app.pet-intent.base-url})을 쓴다 — 같은 서버이고, 주소가 두 군데에
 * 적히면 한쪽만 고쳐지는 날이 온다.
 *
 * <p>
 * 타임아웃만 따로 둔다. 의도 분석은 3초면 되지만 크롤러는 <b>페이지마다 1초를 쉬고</b> 외부 사이트를
 * 받아오므로(22페이지면 25초 이상) 같은 값을 쓸 수 없다.
 */
@Slf4j
@Component
public class SeoulPetNewsClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SeoulPetNewsClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.pet-intent.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.pet-news.timeout-ms:60000}") long timeoutMs) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
        log.info("[SeoulPetNewsClient] 초기화 baseUrl={} timeoutMs={}", baseUrl, timeoutMs);
    }

    /**
     * 크롤러를 호출한다.
     *
     * @return 응답. 호출 자체가 실패하면 {@code error} 가 채워진 빈 응답 — <b>null 을 돌려주지 않는다.</b>
     * 호출부가 null 검사 대신 이력 기록에 집중할 수 있어야 한다.
     */
    public SeoulPetNewsCrawlResponse fetch(int maxPages) {
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/crawl/seoul-pet-news")
                .queryParam("max_pages", maxPages)
                .toUriString();
        try {
            SeoulPetNewsCrawlResponse response = restTemplate.getForObject(url, SeoulPetNewsCrawlResponse.class);
            if (response == null) {
                return emptyWithError("크롤러 응답 본문이 비어 있다");
            }
            return response;
        } catch (RestClientException e) {
            log.error("크롤러 호출 실패 url={} error={}", url, e.getMessage());
            return emptyWithError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private SeoulPetNewsCrawlResponse emptyWithError(String message) {
        return new SeoulPetNewsCrawlResponse(null, 0, 0, 0, java.util.List.of(), message);
    }
}
