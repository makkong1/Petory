package com.linkup.Petory.domain.location.dto;

import java.util.List;

/**
 * odcloud 오픈API 한 페이지 파싱 결과.
 *
 * @param items      이 페이지의 시설 목록
 * @param totalCount 전체 건수(응답 matchCount/totalCount)
 */
public record PublicDataApiPage(List<PublicDataLocationDTO> items, int totalCount) {
}
