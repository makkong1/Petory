package com.linkup.Petory.domain.location.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code petory-nlp-server} 의 {@code GET /crawl/seoul-pet-news} 응답.
 *
 * <p>
 * 크롤러는 실패해도 500 을 내지 않는다. {@code items} 가 비고 {@code error} 가 채워져 200 으로 온다
 * — 그래야 동기화 이력에 「무엇 때문에 0건이었는지」가 남는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulPetNewsCrawlResponse(
        String source,
        /** 목록에서 본 행 수. items 수가 아니다. */
        int fetched,
        /** 행 구조가 깨져 파싱 못 한 수. */
        int skippedInvalid,
        /** 제목에 반려동물 신호어가 없어 거른 수. 검색이 본문까지 훑어 무관 기사가 섞인다. */
        int skippedUnclassified,
        List<Item> items,
        String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String source,
            String sourceNo,
            String title,
            String dept,
            LocalDate publishedAt,
            String sourceUrl,
            String category) {
    }

    public List<Item> safeItems() {
        return items == null ? List.of() : items;
    }
}
